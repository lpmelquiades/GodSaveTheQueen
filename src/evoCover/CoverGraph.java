package evoCover;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;
import graph.*;

public class CoverGraph {

	List<Portfolio> solutionList;
	Graph<StockLog, CoverLink> graph;
	List<Integer> dayList;
	String fileName;
	int solutionQtt;
	String plotData;

	public CoverGraph(int sqtt, String fileName) throws IOException {
		solutionQtt = sqtt;
		solutionList = new ArrayList<>();
		for (int i = 0; i < solutionQtt; i++) {
			solutionList.add(new Portfolio(i));
		}
		init(fileName);
		initAllRelations();
	}

	public List<Portfolio> getSolutionList() {
		return solutionList;
	}

	public void markGraph(double corrTLimit, double meanBLimit, double semiVarTLimit) {

		for (Vertex<StockLog, CoverLink> vertex : graph.getVertexList()) {
			if (vertex.getContent().mean < meanBLimit && vertex.getContent().variance > semiVarTLimit) {
				vertex.getContent().flag = -1;
			} else {
				vertex.getContent().flag = 0;
			}
		}

		for (Edge<StockLog, CoverLink> edge : this.graph.getEdgeList()) {
			if (edge.getRelation().correlation > corrTLimit) {
				edge.getRelation().flag = -1;
			} else {
				edge.getRelation().flag = 0;
			}
		}

	}

	private void initAllRelations() {
		int j;
		CoverLink link;
		int size = graph.getVertexList().size();
		double corr;
		for (int i = 0; i < size; i++) {
			j = i + 1;
			while (j < size) {
				corr = graph.getContent(i).calcCorrelation(graph.getContent(j));
				link = new CoverLink();
				link.correlation = corr;
				graph.addRelation(i, j, link);
				link = new CoverLink();
				link.correlation = corr;
				graph.addRelation(j, i, link);
				j++;
			}
		}
	}

	public List<Vertex<StockLog, CoverLink>> getVertexList() {
		return graph.getVertexList();
	}

	public void randomInit() {
		for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
			CoverLink link = e.getRelation();
			for (int i = 0; i < solutionQtt; i++) {
				link.coverList.add(new Random().nextDouble());
			}
		}
		normalizeAll();
	}

	public void normalize(int solutionId) {
		double total, normCover;
		total = 0;
		for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
			CoverLink link = e.getRelation();
			total += link.coverList.get(solutionId);
		}
		for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
			CoverLink link = e.getRelation();
			normCover = link.coverList.get(solutionId) / total;
			link.coverList.set(solutionId, normCover);
		}
	}

	public void normalizeAll() {
		for (Portfolio p : solutionList) {
			normalize(p.id);
		}
	}

	public void calcMaxAndMinForAll() {
		for (Portfolio p : solutionList) {
			calcMaxAndMin(p);
		}
	}

	public void calcMaxAndMin(Portfolio p) {
		double w;
		w = 0;
		Vertex<StockLog, CoverLink> v0 = graph.getVertexList().get(0);
		for (Edge<StockLog, CoverLink> e : v0.getEdgeList()) {
			w += e.getRelation().coverList.get(p.id);
		}
		p.maxW = w;
		p.minW = w;
		for (Vertex<StockLog, CoverLink> v : graph.getVertexList()) {
			w = 0;
			for (Edge<StockLog, CoverLink> e : v.getEdgeList()) {
				w += e.getRelation().coverList.get(p.id);
			}
			if (p.maxW < w)
				p.maxW = w;
			if (p.minW > w)
				p.minW = w;
		}

	}

	public Portfolio lottery(Measure m) {
		double total = 0;
		Portfolio p;
        for (int i = 0; i < solutionQtt; i++) {
        	p = solutionList.get(i); 
    		total += m.getValue(p);
		}
		double limit = Math.abs((new Random().nextDouble()) * total);
		double part = 0;
		int i = 0;
		do {
			p = solutionList.get(i);
			part += m.getValue(p);
			i++;
		} while ((i < solutionQtt) && (part < limit));
		return p;
	}

	// [20]
	public void onlookerBeePhase20(Measure measure) {
		System.out.println("------");
		Portfolio p2;
		for (Portfolio p1 : solutionList) {
			p2 = lottery(measure);
			System.out.println(p2.id);
			if (measure.getComparator().compare(p1, p2) > 0) {
				System.out.println("onlooker");
				for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
					e.getRelation().coverList.set(p1.id, e.getRelation().coverList.get(p2.id));
				}
			}
		}
	}

	public void scoutBeePhase20(int empQtt, int limit) {
		System.out.println("------");
		for (int i = 0; i < empQtt; i++) {
			if (solutionList.get(i).trail > limit) {
				System.out.println("scout");
				solutionList.get(i).trail = 0;
				for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
					e.getRelation().coverList.add(new Random().nextDouble());
				}
				normalize(solutionList.get(i).id);
			}
		}

	}

	// [20]
	public void employedBeePhase20(int c, Measure measure) {
		calcSemiVarAndSkewnessForAll();
		calcMaxAndMinForAll();
		double newCover;
		Rank rank = new Rank(solutionList, measure);
		Portfolio p2,pNew;
		int bestId = rank.getFirst().id;

		for (Portfolio p1 : solutionList) {
			p2 = lottery(measure);
			p1.trail++;
			for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
				newCover = e.getRelation().coverList.get(p1.id)
						+ new Random().nextDouble()
								* (e.getRelation().coverList.get(p1.id) - e.getRelation().coverList.get(p2.id))
						+ new Random().nextInt(c + 1)
								* (e.getRelation().coverList.get(bestId) - e.getRelation().coverList.get(p1.id));
					e.getRelation().coverList.add(Math.max(0, newCover));
			}
			pNew = new Portfolio(graph.getEdgeList().get(0).getRelation().coverList.size()-1);
			normalize(pNew.id);
			calcMeanReturn(pNew);
			calcSemiVarAndSkewness(pNew);
			calcMaxAndMin(pNew);
			if(measure.getComparator().compare(p1, pNew) > 0){
				p1.trail = 0;
				for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
					e.getRelation().coverList.set(p1.id, e.getRelation().coverList.get(pNew.id));
					e.getRelation().coverList.remove(pNew.id);
				}
			}
		}
	}

	public String formatHeader(Measure measure) {
		String header = "id," + measure.getClass().getSimpleName() + ",Mean,SemiVar";
		for (Vertex<StockLog, CoverLink> v : graph.getVertexList())
			header += "," + v.getContent().tradeCode;
		return header;
	}

	public String formatResult(Measure measure) {
		Rank rank = new Rank(solutionList, measure);
		DecimalFormat df = new DecimalFormat("#0.000000");
		double weight;
		String text = "";

		Portfolio p;

		for (int i = 0; i < 3; i++) {
			p = rank.rankedList.get(i);
			text += p.id + "," + df.format(measure.getValue(p)) + "," + df.format(p.mean) + "," + df.format(p.semiVar);
			for (Vertex<StockLog, CoverLink> v : graph.getVertexList()) {
				weight = 0;
				for (Edge<StockLog, CoverLink> e : v.getEdgeList()) {
					weight += e.getRelation().coverList.get(p.id);
				}
				text += "," + df.format(weight);
			}
			text += "\n";
		}
		return text;
	}

	public String getMeanAndSemiVar() {
		String out = "";
		for (Portfolio p : solutionList) {
			out += p.mean + "," + p.semiVar + "\n";
		}
		return out;
	}

	public void printResult(Measure measure) {
		System.out.print(formatResult(measure));
		System.out.println("------------");
	}

	public void printResult(String resultFile, Measure measure) throws FileNotFoundException {
		PrintWriter writer = new PrintWriter(resultFile);
		writer.print(formatResult(measure));
		writer.close();
	}

	public void fixGraph(int coupleQtt, Measure m) {
		calcSemiVarAndSkewnessForAll();
		calcMaxAndMinForAll();
		Rank rank = new Rank(solutionList, m);
		swapCoverListValues(rank.rankedList, buildSwapList(coupleQtt, rank.rankedList));
		solutionList = rank.rankedList;
		fixCoverListForAllEdges(coupleQtt);
	}

	public List<Portfolio> buildSwapList(int coupleQtt, List<Portfolio> rankedList) {
		Portfolio p2;
		List<Portfolio> swapList = new ArrayList<>();
		for (int i = 0; i < coupleQtt * 2; i++) {
			p2 = rankedList.remove(rankedList.size() - 1);
			if (p2.id < solutionQtt) {
				swapList.add(p2);
			}
		}
		return swapList;
	}

	public void swapCoverListValues(List<Portfolio> rankedList, List<Portfolio> swapList) {
		int k = 0;
		for (Portfolio p : rankedList) {
			if (p.id >= solutionQtt) {
				for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
					e.getRelation().coverList.set(swapList.get(k).id, e.getRelation().coverList.get(p.id));
				}
				p.id = swapList.get(k).id;
				k++;
			}
		}
	}

	public void fixCoverListForAllEdges(int coupleQtt) {
		for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
			for (int i = 0; i < coupleQtt * 2; i++) {
				e.getRelation().coverList.remove(e.getRelation().coverList.size() - 1);
			}
		}
	}

	// [10]
	public void crossOver10(int coupleQtt, Measure measure) {
		calcSemiVarAndSkewnessForAll();
		calcMaxAndMinForAll();
		Portfolio solution1, solution2;
		int id, i = 0;
		double cover1, cover2, rand, beta;
		while (i < coupleQtt) {
			i++;
			solution1 = lottery(measure);
			solution2 = lottery(measure);
			rand = new Random().nextDouble();
			id = graph.getEdgeList().get(0).getRelation().coverList.size();
			for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
				cover1 = e.getRelation().coverList.get(solution1.id);
				cover2 = e.getRelation().coverList.get(solution2.id);
				if (rand <= 0.5)
					beta = Math.pow(2 * rand, 1 / (1 + graph.getEdgeList().size()));
				else
					beta = Math.pow(1 / (2 * (1 - rand)), 1 / (1 + graph.getEdgeList().size()));
				e.getRelation().coverList.add((cover1 * (1 + beta) + cover2 * (1 - beta)) / 2);
				e.getRelation().coverList.add((cover1 * (1 - beta) + cover2 * (1 + beta)) / 2);
			}
			solutionList.add(new Portfolio(id));
			solutionList.add(new Portfolio(id + 1));
			normalize(id);
			normalize(id + 1);
		}
		fixGraph(coupleQtt, measure);
	}

	// [16]
	public void crossOver16(int coupleQtt, Measure measure) {
		calcSemiVarAndSkewnessForAll();
		calcMaxAndMinForAll();
		Portfolio solution1, solution2;
		int id, i = 0;
		double c1, c2, rand;
		while (i < coupleQtt) {
			i++;
			solution1 = lottery(measure);
			solution2 = lottery(measure);
			rand = new Random().nextDouble();
			id = graph.getEdgeList().get(0).getRelation().coverList.size();
			for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
				c1 = e.getRelation().coverList.get(solution1.id);
				c2 = e.getRelation().coverList.get(solution2.id);
				e.getRelation().coverList.add(rand * c1 + (1 - rand) * c2);
				e.getRelation().coverList.add(rand * c2 + (1 - rand) * c1);
			}
			solutionList.add(new Portfolio(id));
			solutionList.add(new Portfolio(id + 1));
			normalize(id);
			normalize(id + 1);
		}
		fixGraph(coupleQtt, measure);
	}

	// [10]
	public void mutation10() {
		calcMaxAndMinForAll();
		double rand, deltaQ, delta, cover, newCover;
		double chance = 1.0 / graph.getEdgeList().size();
		int i;
		for (Portfolio p : solutionList) {
			i = 0;
			for (Edge<StockLog, CoverLink> e1 : graph.getEdgeList()) {
				if (new Random().nextDouble() < chance) {
					System.out.println("mutation10");
					rand = new Random().nextDouble();
					cover = e1.getRelation().coverList.get(p.id);
					if (rand <= 0.5) {
						delta = (cover - p.minW) / (p.maxW - p.minW);
						deltaQ = Math.pow(2 * rand + (1 - 2 * rand) * Math.pow((1 - delta), i + 1), 1 / (i + 1)) - 1;
					} else {
						delta = (p.maxW - cover) / (p.maxW - p.minW);
						deltaQ = 1 - Math.pow(2 * (1 - rand) + 2 * (rand - 0.5) * Math.pow((1 - delta), i + 1),
								1 / (i + 1));
					}
					newCover = cover + deltaQ * (p.maxW - p.minW);
					e1.getRelation().coverList.set(p.id, newCover);

				}
				i++;
			}
			normalize(p.id);
		}
	}

	// [15]
	public void mutation15() {
		double tradeE1, tradeE2;
		int e2Id;
		double chance = 1.0 / graph.getEdgeList().size();
		for (Portfolio p : solutionList) {
			for (Edge<StockLog, CoverLink> e1 : graph.getEdgeList()) {
				if (new Random().nextDouble() < chance) {
					System.out.println("mutation15");
					tradeE1 = e1.getRelation().coverList.get(p.id);
					e2Id = new Random().nextInt(graph.getEdgeList().size());
					tradeE2 = graph.getEdgeList().get(e2Id).getRelation().coverList.get(p.id);
					e1.getRelation().coverList.set(p.id, tradeE2);
					graph.getEdgeList().get(e2Id).getRelation().coverList.set(p.id, tradeE1);
				}
			}
			normalize(p.id);
		}
	}

	// [16]
	public void mutation16(double chance) {
		double newCover;
		for (Portfolio p : solutionList) {
			if (new Random().nextDouble() < chance) {
				System.out.println("mutation16");
				for (Edge<StockLog, CoverLink> e : graph.getEdgeList()) {
					newCover = e.getRelation().coverList.get(p.id) * (new Random().nextDouble()) * 2;
					e.getRelation().coverList.set(p.id, newCover);
				}
			}
			normalize(p.id);
		}
	}

	public void calcSemiVarAndSkewness(Portfolio p) {
		calcMeanReturnForAll();
		double term, semiTerm, skewTerm, varTerm, weight;
		List<Double> dayReturnList;
		dayReturnList = new ArrayList<>();
		for (int i = 0; i < dayList.size(); i++) {
			dayReturnList.add(0.0);
		}
		for (Vertex<StockLog, CoverLink> v : graph.getVertexList()) {
			weight = 0;
			for (Edge<StockLog, CoverLink> e : v.getEdgeList()) {
				weight += e.getRelation().coverList.get(p.id);
			}
			int t = 0;
			for (Double r : v.getContent().returnLog) {
				term = r * weight + dayReturnList.get(t);
				dayReturnList.set(t, term);
				t++;
			}
		}
		semiTerm = 0;
		skewTerm = 0;
		varTerm = 0;
		for (Double d : dayReturnList) {
			semiTerm += Math.min(0, (d - p.mean)) * Math.min(0, (d - p.mean));
			varTerm += (d - p.mean) * (d - p.mean);
		}
		p.semiVar = semiTerm / (dayReturnList.size() - 1);
		varTerm /= (dayReturnList.size() - 1);
		for (Double d : dayReturnList) {
			skewTerm += Math.pow((d - p.mean) / Math.pow(varTerm, 1 / 2), 3);
		}
		p.semiVar = semiTerm / (dayReturnList.size() - 1);
		p.skewness = skewTerm / (dayReturnList.size() - 1);
	}

	public void calcSemiVarAndSkewnessForAll() {
		calcMeanReturnForAll();
		for (Portfolio p : solutionList) {
			calcSemiVarAndSkewness(p);
		}
	}

	public void calcMeanReturnForAll() {
		for (Portfolio p : solutionList) {
			calcMeanReturn(p);
		}
	}

	public void calcMeanReturn(Portfolio p) {
		double term, weight;
		term = 0;
		for (Vertex<StockLog, CoverLink> v : graph.getVertexList()) {
			weight = 0;
			for (Edge<StockLog, CoverLink> e : v.getEdgeList()) {
				weight += e.getRelation().coverList.get(p.id);
			}
			term += (v.getContent().mean * weight);
		}
		p.mean = term;
	}

	private void init(String fileName) throws IOException {
		graph = new Graph<>();
		dayList = new ArrayList<>();
		this.fileName = fileName;
		BufferedReader reader = new BufferedReader(new FileReader(fileName));
		String line = reader.readLine();
		String[] splitLine = line.split(",");
		for (int i = 1; i < splitLine.length; i++) {
			dayList.add(Integer.parseInt(splitLine[i]));
		}
		StockLog log;
		while (((line = reader.readLine()) != null)) {
			log = new StockLog(line);
			graph.addContent(log);
		}
		reader.close();
	}

	public void writeFilteredCSVFile(String dirName, double corrTLimit, double meanBLimit, double semiVarTLimit)
			throws IOException {
		int qtt = divideGraph(corrTLimit, meanBLimit, semiVarTLimit);
		PrintWriter writer;
		int k;
		String text, head;
		head = "tradeCode";
		for (Integer day : dayList) {
			head += "," + day;
		}
		for (int i = -1; i < qtt; i++) {
			text = head + "\n";
			k = 0;
			for (Vertex<StockLog, CoverLink> vertex : this.graph.getVertexList()) {
				if (vertex.getContent().flag == i) {
					k++;
					text += vertex.getContent().toString();
					text += "\n";
				}
			}

			if (k > 7) {
				writer = new PrintWriter(dirName + "part" + i + ".csv");
				writer.print(text);
				writer.close();
			}
		}
	}

	public void countLevelForAll() {
		int count;

		for (Vertex<StockLog, CoverLink> v : graph.getVertexList()) {
			count = 0;
			for (Edge<StockLog, CoverLink> edge : v.getEdgeList()) {
				if (edge.getRelation().flag != -1 && edge.getVertexB().getContent().flag != -1) {
					count++;
				}
			}
			v.getContent().level = count;

		}
	}

	public Comparator<Vertex<StockLog, CoverLink>> getLevelAscComparator() {
		return new Comparator<Vertex<StockLog, CoverLink>>() {
			@Override
			public int compare(Vertex<StockLog, CoverLink> o1, Vertex<StockLog, CoverLink> o2) {
				return o1.getContent().level.compareTo(o2.getContent().level);
			}
		};
	}

	public Comparator<Vertex<StockLog, CoverLink>> getLevelDescComparator() {
		return new Comparator<Vertex<StockLog, CoverLink>>() {
			@Override
			public int compare(Vertex<StockLog, CoverLink> o1, Vertex<StockLog, CoverLink> o2) {
				return o2.getContent().level.compareTo(o1.getContent().level);
			}
		};
	}

	public int divideGraph(double corrTLimit, double meanBLimit, double semiVarTLimit) {
		markGraph(corrTLimit, meanBLimit, semiVarTLimit);
		int initialQtt = 19;
		countLevelForAll();
		PriorityQueue<Vertex<StockLog, CoverLink>> sortedByLevelQ = new PriorityQueue<>(this.getLevelDescComparator());

		for (Vertex<StockLog, CoverLink> v : graph.getVertexList()) {
			sortedByLevelQ.add(v);
		}

		PriorityQueue<Vertex<StockLog, CoverLink>> sortedAdjQ;
		ArrayDeque<Vertex<StockLog, CoverLink>> mainQ = new ArrayDeque<>();

		int mark = 0;
		int qtt = 0;
		Vertex<StockLog, CoverLink> marked, sortedVertex, sortedAdjVertex;
		while (!sortedByLevelQ.isEmpty()) {
			sortedVertex = sortedByLevelQ.poll();
			if (sortedVertex.getContent().flag == 0) {
				mark++;
				qtt = initialQtt;
				sortedVertex.getContent().flag = mark;
				mainQ.add(sortedVertex);
			}
			while (!mainQ.isEmpty()) {
				marked = mainQ.poll();
				sortedAdjQ = new PriorityQueue<>(this.getLevelDescComparator());

				for (Edge<StockLog, CoverLink> edge : marked.getEdgeList()) {
					if (edge.getRelation().flag != -1 && edge.getVertexB().getContent().flag == 0) {
						sortedAdjQ.add(edge.getVertexB());
					}
				}

				while (qtt > 0 && !sortedAdjQ.isEmpty()) {
					sortedAdjVertex = sortedAdjQ.poll();
					sortedAdjVertex.getContent().flag = mark;
					mainQ.add(sortedAdjVertex);
					qtt--;
				}
			}

		}

		return mark + 1;
	}

}
