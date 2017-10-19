/*
 * Functional Map of the World Visualizer and Offline Tester
 * by walrus71
 * 
 * Version history:
 * ================
 * 1.0 (2017.09.14.)
 *      - First public version at contest launch
 *      - Bugfix: storage_tank category was missing
 * 0.4 (2017.09.08.)
 *      - Supporting multiple boxes per image
 *      - Added 'show box ids' switch
 *      - Added coordinate info
 * 0.3 (2017.08.18.)
 *      - Added 'show labels' switch
 *      - Ordered json output
 *      - Reading/writing TOC file
 * 0.2 (2017.08.16.)
 *      - Rolled up temporal views in list
 *      - Added scale ruler
 *      - Added 'show only errors' switch
 * 0.1 (2017.08.14.)
 *      - First internal version
 */
package visualizer;

import static visualizer.Utils.f;
import static visualizer.Utils.f6;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class FmowVisualizer implements ActionListener, MouseListener {
	private static final String FALSE_DETECTION = "false_detection";
	private static final String FALSE_DETECTION_ALIAS = "unknown"; // to be backward compatible with an earlier name
	private static final String TITLE = "fMoW Visualizer";
	public static final String ARROW = " \u21d2 ";
	public static final String TOC_FILE_NAME = "toc.txt";
	
	private boolean createTOC = false;
	private PrintWriter tocWriter = null;
	private boolean hasGui = true;
	private String dataDir;
	private Map<String, Metrics> categoryToScore;
	private Map<Integer, String> boxIdToCategory;
	private Map<Integer, String> boxIdToGuess;
	private Map<Integer, String> boxIdToSceneId;
	private Map<String, Scene> sceneIdToScene;
	private Scene[] scenes;
	private Set<String> categorySet; // all known categories
	private Map<String, Double> categoryWeights; // label->w
	private Scene currentScene;
	private MapData currentMapData;
	private Box[] currentBoxes;
	private double currentGsd;
	private String solutionPath;
	private int maxNperCategory = Integer.MAX_VALUE;
	private boolean useMsData = true;
	private String sceneFilter = null;
	private Pattern sceneFilterPattern = null;
	private GsonBuilder jsonBuilder;
	private Gson gson;
	private boolean writeSolution = false; // TODO false, just for debugging, ignore 
	
	private double scale; // data size / screen size
	private double x0 = 0, y0 = 0; // x0, y0: TopLeft corner of data is shown here (in screen space)
	private double[] rulerLengths = {1, 2, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 10000};
	private String[] rulerLabels = {"Come on, this is not a microscope!", "2m", "5m", "10m", "20m", "50m", 
			"100m", "200m", "500m", "1km", "2km", "5km", "10km"};
	private int rulerIndex;
	
	private JFrame frame;
	private JPanel viewPanel, controlsPanel;
	private JCheckBox showBoxesCb;
	private JCheckBox showLabelsCb;
	private JCheckBox showBoxIdsCb;
	private JCheckBox errorsOnlyCb;
	private JLabel xyInfoLabel;
	private JTextArea logArea;
	private JTextArea infoArea;
	private MapView mapView;
	private Font font = new Font("SansSerif", Font.BOLD, 16);
	
	private Color textColor             = Color.black;
	private Color borderColorBlack      = new Color(  0,   0,   0, 200);
	private Color borderColorWhite      = new Color(255, 255, 255, 200);
	
	private void run() throws Exception {
		// the complete list of known categories
		String categories = "airport,airport_hangar,airport_terminal,amusement_park,aquaculture,archaeological_site,"
				+ "barn,border_checkpoint,burial_site,car_dealership,construction_site,crop_field,dam,"
				+ "debris_or_rubble,educational_institution,electric_substation,factory_or_powerplant,fire_station,"
				+ "flooded_road,fountain,gas_station,golf_course,ground_transportation_station,helipad,hospital,"
				+ "impoverished_settlement,interchange,lake_or_pond,lighthouse,military_facility,"
				+ "multi-unit_residential,nuclear_powerplant,office_building,oil_or_gas_facility,park,"
				+ "parking_lot_or_garage,place_of_worship,police_station,port,prison,race_track,railway_bridge,"
				+ "recreational_facility,road_bridge,runway,shipyard,shopping_mall,single-unit_residential,"
				+ "smokestack,solar_farm,space_facility,stadium,storage_tank,surface_mine,swimming_pool,toll_booth,tower,"
				+ "tunnel_opening,waste_disposal,water_treatment_facility,wind_farm,zoo";
		String[] catArr = categories.split(",");
		categorySet = new HashSet<>();
		categoryWeights = new HashMap<>();
		for (String cat: catArr) {
			categorySet.add(cat);
			categoryWeights.put(cat, 1.0);
		}
		
		// low weight
		categories = "wind_farm,tunnel_opening,solar_farm,nuclear_powerplant,military_facility,crop_field,airport,"
				+ "flooded_road,debris_or_rubble,single-unit_residential";
		catArr = categories.split(",");
		for (String cat: catArr) {
			categoryWeights.put(cat, 0.6);
		}
		// high weight
		categories = "border_checkpoint,construction_site,educational_institution,factory_or_powerplant,fire_station,"
				+ "police_station,gas_station,smokestack,tower,road_bridge";
		catArr = categories.split(",");
		for (String cat: catArr) {
			categoryWeights.put(cat, 1.4);
		}
		
		// spec
		categorySet.add(FALSE_DETECTION);
		categoryWeights.put(FALSE_DETECTION, 0.0);
		
		jsonBuilder = new GsonBuilder();
		jsonBuilder.setPrettyPrinting();
		gson = jsonBuilder.create();
		
		if (sceneFilter != null) {
			sceneFilterPattern = Pattern.compile(sceneFilter);
		}
		
		loadTruth();
		
		if (createTOC) {
			createTOC();
			log("TOC file created, re-run application without the -toc setting.");
			System.exit(0);
		}
		
		loadSolution();
		
		// some false_detection box may be still called unknown, unify these
		for (int b: boxIdToCategory.keySet()) {
			String cat = boxIdToCategory.get(b);
			if (cat.equals(FALSE_DETECTION_ALIAS)) boxIdToCategory.put(b, FALSE_DETECTION);
		}
		for (int b: boxIdToGuess.keySet()) {
			String cat = boxIdToGuess.get(b);
			if (cat.equals(FALSE_DETECTION_ALIAS)) boxIdToGuess.put(b, FALSE_DETECTION);
		}
		
		categoryToScore = new HashMap<>();
		if (boxIdToCategory.isEmpty() || boxIdToGuess.isEmpty()) {
			log("Nothing to score");
		}
		else {
			for (int boxId: boxIdToCategory.keySet()) {
				String category = boxIdToCategory.get(boxId);
				Metrics m = categoryToScore.get(category);
				if (m == null) {
					m = new Metrics();
					categoryToScore.put(category, m);
				}
				String guess = boxIdToGuess.get(boxId);
				if (category.equals(guess)) {
					m.tp++;
				}
				else {
					m.fn++;
					Metrics mGuess = categoryToScore.get(guess);
					if (mGuess == null) {
						mGuess = new Metrics();
						categoryToScore.put(guess, mGuess);
					}
					mGuess.fp++;
					
					String sceneId = boxIdToSceneId.get(boxId);
					if (sceneId != null) {
						Scene s = sceneIdToScene.get(sceneId);
						if (s != null) {
							s.isError = true;
							s.guess = guess;
						}						
					}
				}
			}
			
			if (categoryToScore.isEmpty() || 
					(categoryToScore.size() == 1 && categoryToScore.containsKey(FALSE_DETECTION))) {
				// can happen if no truth data, everything is UNKNOWN
				log("Nothing to score");
			}
			else {
				String scoreText = getScoreText();
				log(scoreText);
			}
		} // anything to score
		
		// the rest is for UI, not needed for scoring
		if (!hasGui) return;
		
		if (categoryToScore.isEmpty()) {
			errorsOnlyCb.setEnabled(false);
		}
		writeImageList(false);
		logArea.setCaretPosition(0);
		currentScene = scenes[0];
		loadImage(currentScene.tList.get(0));
		repaintMap();
	}
	
	private void createTOC() {
		try {
			Map<String, Set<Integer>> sceneIdToBoxIds = new HashMap<>();
			for (int boxId: boxIdToSceneId.keySet()) {
				String sceneId = boxIdToSceneId.get(boxId);
				Set<Integer> ids = sceneIdToBoxIds.get(sceneId);
				if (ids == null) {
					ids = new HashSet<>();
					sceneIdToBoxIds.put(sceneId, ids);
				}
				ids.add(boxId);
			}
			
			String dataDirPath = new File(dataDir).getCanonicalPath();
			int len = dataDirPath.length();
			tocWriter = new PrintWriter(new BufferedWriter(new FileWriter(new File(dataDir, TOC_FILE_NAME))));
			for (Scene scene: scenes) {
				StringBuilder sb = new StringBuilder();
				// sceneId<tab>local_path<tab>tmpid1;tmpid2;...<tab>[boxid1;category1<tab>]+
				
				sb.append(scene.id);
				String path = scene.dir.getCanonicalPath().substring(len);
				sb.append("\t").append(path);
				
				sb.append("\t");
				for (int tmpid: scene.tList) {
					sb.append(tmpid).append(";");
				}
				
				Set<Integer> ids = sceneIdToBoxIds.get(scene.id);
				for (int id: ids) {
					sb.append("\t").append(id).append(";").append(boxIdToCategory.get(id));
				}
				sb.append("\n");
				tocWriter.print(sb.toString());
			}
			tocWriter.close();			
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String getScoreText() {
		String[] categories = categoryToScore.keySet().toArray(new String[0]);
		Arrays.sort(categories);
		
		StringBuilder sb = new StringBuilder();
		double fSum = 0;
		double wSum = 0;
		for (String cat: categories) {
			Metrics m = categoryToScore.get(cat);
			m.calculate();
			double w = categoryWeights.get(cat);
			fSum += m.fScore * w;
			wSum += w;
		}
		double f = 0;
		if (wSum > 0) f = fSum / wSum;
		sb.append("\nOverall F-score : " + f6(f)).append("\n");
		
		sb.append("  " + pad("--category--", 16) + "F-score; TP; FP; FN; precision; recall; weight").append("\n");
		for (String cat: categories) {
			Metrics m = categoryToScore.get(cat);
			sb.append("  ").append(pad(cat, 16)) 
				.append(f(m.fScore)).append("; ")
				.append(m.tp).append("; ")
				.append(m.fp).append("; ")
				.append(m.fn).append("; ")
				.append(f(m.precision)).append("; ")
				.append(f(m.recall)).append("; ")
				.append(f(categoryWeights.get(cat))).append("\n");				
		}
		return sb.toString();
	}

	private String pad(String s, int len) {
		if (s.length() > len-1) s = s.substring(0, len-1);
		while (s.length() < len) s += " ";
		return s;
	}
	
	private void loadTruth() {
		log("Reading truth data from " + dataDir + " ...");
		boxIdToCategory = new HashMap<>();
		boxIdToSceneId = new HashMap<>();
		sceneIdToScene = new HashMap<>();
		
		if (!createTOC && new File(dataDir, TOC_FILE_NAME).exists()) {
			log("  using TOC file");
			loadTruthFromToc();
		}
		else {
			recurseTruthDir(new File(dataDir));
		}
		
		scenes = sceneIdToScene.values().toArray(new Scene[0]);
		Arrays.sort(scenes);
		
		if (writeSolution) {
			writeSolution();
		}
	}
	
	private void loadTruthFromToc() {
		try {
			String dataDirPath = new File(dataDir).getCanonicalPath();
			boolean hasLimit = maxNperCategory < Integer.MAX_VALUE;
			Map<String, Integer> categoryCounts = new HashMap<>();
			if (hasLimit) {
				for (String cat: categorySet) categoryCounts.put(cat, 0);
			}
			LineNumberReader lnr = new LineNumberReader(new FileReader(new File(dataDir, TOC_FILE_NAME)));
	        while (true) {
				String line = lnr.readLine();
				if (line == null) break;
				// airport_101	\airport\airport_101	0;2;	31167;airport
				String[] parts = line.split("\t");
				String sceneId = parts[0];
				if (sceneFilterPattern != null) {
					Matcher m = sceneFilterPattern.matcher(sceneId);
					if (!m.find()) {
						continue;
					}
				}
				if (hasLimit) {
					String cat = parts[3].split(";")[1]; // use only the first box, there's only 1 in training data
					if (categorySet.contains(cat) && categoryCounts.get(cat) >= maxNperCategory) {
						continue;
					}
					int cnt = categoryCounts.get(cat);
					categoryCounts.put(cat, cnt + 1);
				}
				
				Scene scene = new Scene();
				scene.id = sceneId;
				scene.dir = new File(dataDirPath + parts[1]);
				String[] tIds = parts[2].split(";");
				for (String t: tIds) {
					scene.tList.add(Integer.parseInt(t));
				}
				sceneIdToScene.put(sceneId, scene);
				
				for (int i = 3; i < parts.length; i++) {
					String[] boxCat = parts[i].split(";");
					int boxId = Integer.parseInt(boxCat[0]);
					String cat = boxCat[1];
					boxIdToCategory.put(boxId, cat);
					boxIdToSceneId.put(boxId, sceneId);
				}
			}
			lnr.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void recurseTruthDir(File dir) {
		int dirCnt = 0;
		for (File f: dir.listFiles()) {
			if (f.isDirectory()) {
				recurseTruthDir(f);
				dirCnt++;
				if (dirCnt == maxNperCategory && categorySet.contains(dir.getName())) {
					break;
				}
			}
			else {
				String suffix = useMsData ? "_msrgb.json" : "_rgb.json";
				if (f.getName().endsWith(suffix)) {
					// train/airport/airport_0/airport_0_0_msrgb.json
					String imageName = f.getName();
					String[] parts = imageName.split("_");
					int n = parts.length;
					String sceneId = "";
					for (int i = 0; i < n-2; i++) {
						sceneId += parts[i];
						if (i < n-3) sceneId += "_";
					}
					if (sceneFilterPattern != null) {
						Matcher m = sceneFilterPattern.matcher(sceneId);
						if (!m.find()) {
							continue;
						}
					}
					
					int timeId = Integer.parseInt(parts[n-2]);
					
					// do we have the corresponding jpg?
					imageName = imageName.replace(".json", ".jpg");
					File imageF = new File(dir, imageName);
					Scene scene = null;
					if (imageF.exists()) {
						scene = sceneIdToScene.get(sceneId);
						if (scene == null) {
							scene = new Scene();
							scene.id = sceneId;
							scene.dir = dir;
							sceneIdToScene.put(sceneId, scene);
						}
						scene.tList.add(timeId);
					}
					else {
						log("Image file " + sceneId + " not found");
						continue;
					}
					
					try {
						MetaData md = gson.fromJson(new FileReader(f), MetaData.class);
						for (Box b: md.bounding_boxes) {
							int id = b.ID;
							String cat = b.category;
							if (cat == null) cat = FALSE_DETECTION;
							boxIdToCategory.put(id, cat);
							boxIdToSceneId.put(id, sceneId);
						}
					}
					catch (Exception e) {
						log("Error reading meta data from " + f.getAbsolutePath());
						e.printStackTrace();
						System.exit(1);
					}
				} // f ends in json
			} // f is file
		} // for files in dir
	}
	
	// output the perfect expected solution, debug only
	private void writeSolution() {
		try {
			FileOutputStream out = new FileOutputStream("solution.txt");
			for (int id: boxIdToCategory.keySet()) {
				String cat = boxIdToCategory.get(id);
				String line = id + "," + cat + "\n";
				out.write(line.getBytes());
			}
			out.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}
	
	private void loadSolution() {
		boxIdToGuess = new HashMap<>();
		if (solutionPath == null) {
			log("No solution file given.");
			return;
		}
		log("Reading solution data from " + solutionPath + " ...");
		List<String> lines = Utils.readTextLines(solutionPath);
		int lineNo = 0;
		for (String line: lines) {
			lineNo++;
			String[] parts = line.split(",");
			if (parts.length != 2) {
				exit("Wrong format at line " + lineNo + " : " + line);
			}
			int boxId = Integer.parseInt(parts[0].trim());
			String guess = parts[1].trim();
			if (!categorySet.contains(guess)) {
				exit("Unknown category at line " + lineNo + " : " + guess);
			}
			boxIdToGuess.put(boxId, guess);
		}
		// all truth box has to have a prediction
		for (int id: boxIdToCategory.keySet()) {
			if (!boxIdToGuess.containsKey(id)) {
				exit("No prediction found for: " + id);
			}
		}
	}
    
    private void getBestRulerIndex() {
		int wBest = mapView.getWidth() / 4;
		double bestDiff = Double.MAX_VALUE;
		for (int i = 0; i < rulerLengths.length; i++) {
			double w = rulerLengths[i] / currentGsd / scale;
			double diff = Math.abs(w - wBest);
			if (diff < bestDiff) {
				bestDiff = diff;
				rulerIndex = i;
			}
		}		
	}
	
	private class Scene implements Comparable<Scene> {
		public String id;
		public boolean isError;
		public File dir;
		public List<Integer> tList = new Vector<>(); // temporal view ids
		public String guess; // filled in only if guess is wrong
		
		@Override
		public int compareTo(Scene o) {
			return id.compareTo(o.id);
		}
	}

	private class Metrics {
		public int tp;
		public int fp;
		public int fn;
		public double precision = 0;
		public double recall = 0;
		public double fScore = 0;
		
		public void calculate() {
			if (tp + fp > 0) precision = (double)tp / (tp + fp);
			if (tp + fn > 0) recall = (double)tp / (tp + fn);
			if (precision + recall > 0) {
				fScore = 2 * precision * recall / (precision + recall);
			}
		}
	}
	
	private class MapData {
		public int W;
		public int H;
		public int[][] pixels;
		
		public MapData(int w, int h) {
			W = w; H = h;
			pixels = new int[W][H];
		}
	}
	
	public class Box {
		public int ID;
		public String category;
		public int[] box;
		public transient String guess; // should not appear in json
	}
	
	/**************************************************************************************************
	 * 
	 *              THINGS BELOW THIS ARE UI-RELATED, NOT NEEDED FOR SCORING
	 * 
	 **************************************************************************************************/
	
	public void setupGUI(int W) {
		if (!hasGui) return;
		
		frame = new JFrame(TITLE);
		int H = W * 2 / 3;
		frame.setSize(W, H);
		frame.setResizable(false);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		
		Container cp = frame.getContentPane();
		cp.setLayout(new GridBagLayout());
		
		GridBagConstraints c = new GridBagConstraints();
		
		c.fill = GridBagConstraints.BOTH;
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 2;
		c.weighty = 1;
		viewPanel = new JPanel();
		viewPanel.setPreferredSize(new Dimension(H, H));
		cp.add(viewPanel, c);
		
		c.fill = GridBagConstraints.BOTH;
		c.gridx = 1;
		c.gridy = 0;
		c.weightx = 1;
		controlsPanel = new JPanel();
		cp.add(controlsPanel, c);

		viewPanel.setLayout(new BorderLayout());
		mapView = new MapView();
		viewPanel.add(mapView, BorderLayout.CENTER);
		
		controlsPanel.setLayout(new GridBagLayout());
		GridBagConstraints c2 = new GridBagConstraints();
		
		int y = 0;
		
		showBoxesCb = new JCheckBox("Show bounding boxes");
		showBoxesCb.setSelected(true);
		showBoxesCb.addActionListener(this);
		c2.fill = GridBagConstraints.BOTH;
		c2.gridx = 0;
		c2.gridy = y++;
		c2.weightx = 1;
		controlsPanel.add(showBoxesCb, c2);
		
		showLabelsCb = new JCheckBox("Show category labels");
		showLabelsCb.setSelected(true);
		showLabelsCb.addActionListener(this);
		c2.gridy = y++;
		controlsPanel.add(showLabelsCb, c2);
		
		showBoxIdsCb = new JCheckBox("Show box IDs");
		showBoxIdsCb.setSelected(true);
		showBoxIdsCb.addActionListener(this);
		c2.gridy = y++;
		controlsPanel.add(showBoxIdsCb, c2);
		
		errorsOnlyCb = new JCheckBox("Show only images with error");
		errorsOnlyCb.setSelected(false);
		errorsOnlyCb.addActionListener(this);
		c2.gridy = y++;
		controlsPanel.add(errorsOnlyCb, c2);
		
		xyInfoLabel = new JLabel(" XYZ: ");
		c2.gridy = y++;
		controlsPanel.add(xyInfoLabel, c2);
						
		JScrollPane sp = new JScrollPane();
		logArea = new JTextArea("", 10, 20);
		logArea.setFont(new Font("Monospaced", Font.PLAIN, 16));
		logArea.addMouseListener(this);
		sp.getViewport().setView(logArea);
		c2.gridy = y++;
		c2.weighty = 10;
		controlsPanel.add(sp, c2);
		
		sp = new JScrollPane();
		infoArea = new JTextArea("", 10, 20);
		infoArea.setFont(new Font("Monospaced", Font.PLAIN, 16));
		infoArea.addMouseListener(this);
		sp.getViewport().setView(infoArea);
		c2.gridy = y++;
		c2.weighty = 5;
		controlsPanel.add(sp, c2);
		
		frame.setVisible(true);
	}
	
	private void loadImage(int t) {
    	String name = currentScene.id + "_" + t + "_";
    	name += useMsData ? "msrgb.jpg" : "rgb.jpg";
    	frame.setTitle(TITLE + " - " + name);
		File f = new File(currentScene.dir, name);
		try { 
			BufferedImage img = ImageIO.read(f);
			int w = img.getWidth();
			int h = img.getHeight();
			currentMapData = new MapData(w, h);
			for (int i = 0; i < w; i++) for (int j = 0; j < h; j++) {
				int c = img.getRGB(i, j);
				currentMapData.pixels[i][j] = c;
			}
			scale = (double)currentMapData.W / mapView.getWidth(); 
			x0 = 0; y0 = 0;
		} 
		catch (Exception e) {
			log("Error reading image from " + f.getAbsolutePath());
			e.printStackTrace();
		}
		
		// load meta data
		String metaPath = f.getAbsolutePath().replace(".jpg", ".json");
		f = new File(metaPath);
		try {
			MetaData md = gson.fromJson(new FileReader(f), MetaData.class);
			currentGsd = md.gsd;
			currentBoxes = md.bounding_boxes;
			for (Box b: currentBoxes) {
				if (b.category == null || b.category.equals(FALSE_DETECTION_ALIAS)) {
					b.category = FALSE_DETECTION;
				}
				b.guess = boxIdToGuess.get(b.ID);
			}
			getBestRulerIndex();
			
			String formatted = gson.toJson(md);
			infoArea.setText(formatted);
			infoArea.setCaretPosition(0);
		}
		catch (Exception e) {
			log("Error reading meta data from " + f.getAbsolutePath());
			e.printStackTrace();
			System.exit(1);
		}
		frame.repaint();
	}
	
	private void refreshLogArea(boolean errorsOnly) {
		logArea.setText("");
		String scoreText = getScoreText();
		logArea.append(scoreText);
		writeImageList(errorsOnly);
		logArea.setCaretPosition(0);
	}
		
	private void writeImageList(boolean errorsOnly) {
		StringBuilder sb = new StringBuilder();
		sb.append("\n========\n Images\n========\n");
		for (Scene s: scenes) {
			if (!s.isError && errorsOnly) continue;
			
			sb.append(s.isError ? " * " : "   ");
			sb.append(s.id);
			for (int t: s.tList) sb.append(" _").append(t);
			if (s.isError && s.guess != null) {
				sb.append(ARROW).append(s.guess);
			}
			sb.append("\n");
		}
		logArea.append(sb.toString());
	}

	private void repaintMap() {
		if (mapView != null) mapView.repaint();
	}
	
	@SuppressWarnings("serial")
	private class MapView extends JLabel implements MouseListener, MouseMotionListener, MouseWheelListener {
		
		private int mouseX;
		private int mouseY;
		private BufferedImage image;
		private int invalidColor = (50 << 16) | (150 << 8) | 200;
		private int M = 5;		
		
		public MapView() {
			super();
			this.addMouseListener(this);
			this.addMouseMotionListener(this);
			this.addMouseWheelListener(this);
		}		

		@Override
		public void paint(Graphics gr) {
			if (currentMapData == null) return;
			int W = this.getWidth();
			int H = this.getHeight();
			if (image == null) {
				image = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
			}
			
			MapData mapData = currentMapData;
			
			Graphics2D g2 = (Graphics2D) gr;
			g2.setFont(font);
			g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			for (int i = 0; i < W; i++) for (int j = 0; j < H; j++) {
				int c = invalidColor;
				int mapI = (int)((i - x0) * scale);
				int mapJ = (int)((j - y0) * scale);
				
				if (mapI >= 0 && mapJ >= 0 && mapI < mapData.W && mapJ < mapData.H) {
					c = mapData.pixels[mapI][mapJ];
				}
				image.setRGB(i, j, c);
			}
			g2.drawImage(image, 0, 0, null);
			
			if (showBoxesCb.isSelected() && currentBoxes != null) {
				for (Box b: currentBoxes) {
					double minx = b.box[0] / scale + x0;
					if (minx > this.getWidth()) continue;
					double maxx = (b.box[0] + b.box[2]) / scale + x0;
					if (maxx < 0) continue;
					double miny = b.box[1] / scale + y0;
					if (miny > this.getHeight()) continue;
					double maxy = (b.box[1] + b.box[3]) / scale + y0;
					if (maxy < 0) continue;
					int x = (int)(minx);
					int y = (int)(miny);
					int w = (int)(maxx - x);
					int h = (int)(maxy - y);
					
					g2.setColor(borderColorBlack);
					g2.drawRect(x-1, y-1, w+2, h+2);
					g2.setColor(borderColorWhite);
					g2.drawRect(x, y, w, h);
					
					if (showLabelsCb.isSelected()) {
						String label = b.category;
						if (b.guess != null && !b.guess.equals(b.category)) {
							label += ARROW + b.guess; 
						}
						
						w = textWidth(label, g2) + 2*M;
						h = font.getSize() + 2*M;
						g2.setColor(borderColorWhite);
						g2.fillRect(x, y, w, h);
						g2.setColor(textColor);
						g2.drawString(label, x+M, y+h-M);
					}
					if (showBoxIdsCb.isSelected()) {
						String label = b.ID + "";
						w = textWidth(label, g2) + 2*M;
						h = font.getSize() + 2*M;
						g2.setColor(borderColorWhite);
						int yRect = (int)(maxy) - h;
						g2.fillRect(x, yRect, w, h);
						g2.setColor(textColor);
						g2.drawString(label, x+M, yRect+h-M);
					}
				}
			}
			
			int rulerW = (int) (rulerLengths[rulerIndex] / currentGsd / scale);
			if (rulerW < W/2 && rulerW > 3*M) {
				String label = rulerLabels[rulerIndex];
				int w = rulerW + 3*M + textWidth(label, g2);
				int h = font.getSize() + 2*M;
				g2.setColor(borderColorWhite);
				g2.fillRect(M, H-M-h, w, h);
				g2.setColor(textColor);
				g2.drawLine(2*M, H-M-h/2, 2*M+rulerW, H-M-h/2);
				g2.drawString(label, 3*M + rulerW, H-2*M);
			}
		}

		
		private int textWidth(String text, Graphics2D g) {
			FontRenderContext context = g.getFontRenderContext();
			Rectangle2D r = font.getStringBounds(text, context);
			return (int) r.getWidth();
		}

		@Override
		public void mouseClicked(java.awt.event.MouseEvent e) {
			// nothing
		}
		@Override
		public void mouseReleased(java.awt.event.MouseEvent e) {
			repaintMap();
		}
		@Override
		public void mouseEntered(java.awt.event.MouseEvent e) {
			setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
		}
		@Override
		public void mouseExited(java.awt.event.MouseEvent e) {
			setCursor(Cursor.getDefaultCursor());
		}

		@Override
		public void mousePressed(java.awt.event.MouseEvent e) {
			int x = e.getX();
			int y = e.getY();
			mouseX = x;
			mouseY = y;
			repaintMap();
		}
		
		@Override
		public void mouseDragged(java.awt.event.MouseEvent e) {
			int x = e.getX();
			int y = e.getY();
			x0 += x - mouseX;
			y0 += y - mouseY;
			mouseX = x;
			mouseY = y;
			repaintMap();
		}

		@Override
		public void mouseMoved(java.awt.event.MouseEvent e) {
			if (currentMapData == null) return;
			int x = e.getX();
			int y = e.getY();
			int i = (int)((x - x0) * scale);
			int j = (int)((y - y0) * scale);
			String info = "";
			if (i >= 0 && j >= 0 && i < currentMapData.W && j < currentMapData.H) {
				info = i + ", " + j;
			}
			xyInfoLabel.setText(" XY: " + info);
		}

		@Override
		public void mouseWheelMoved(MouseWheelEvent e) {
			mouseX = e.getX();
			mouseY = e.getY();
			double dataX = (mouseX - x0) * scale;
			double dataY = (mouseY - y0) * scale;
			
			double change =  Math.pow(2, 0.5);
			if (e.getWheelRotation() > 0) scale *= change;
			if (e.getWheelRotation() < 0) scale /= change;
			
			x0 = mouseX - dataX / scale;
			y0 = mouseY - dataY / scale;
			
			getBestRulerIndex();
			repaintMap();
		}
	} // class MapView
	

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() == showBoxesCb || e.getSource() == showLabelsCb || e.getSource() == showBoxIdsCb) {
			showLabelsCb.setEnabled(showBoxesCb.isSelected());
			showBoxIdsCb.setEnabled(showBoxesCb.isSelected());
			repaintMap();
		}
		else if (e.getSource() == errorsOnlyCb) {
			refreshLogArea(errorsOnlyCb.isSelected());
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		if (e.getSource() != logArea) return;
		try {
			int lineIndex = logArea.getLineOfOffset(logArea.getCaretPosition());
			int start = logArea.getLineStartOffset(lineIndex);
			int end = logArea.getLineEndOffset(lineIndex);
			String line = logArea.getDocument().getText(start, end - start);
			if (line.length() < 3) return;
			// " * prison_0 _0 _1 _2
			line = line.substring(3);
			int pos = logArea.getCaretPosition() - start - 3;
			String[] parts = line.split(" ");
			if (parts.length < 2) return;
			String id = parts[0];
			Scene scene = sceneIdToScene.get(id);
			if (scene == null) return;
			int len = parts[0].length();
			int tIndex = 0; 
			for (int i = 1; i < parts.length; i++) {
				len += 1 + parts[i].length();
				if (pos <= len) {
					tIndex = i - 1;
					break;
				}
			}
			if (tIndex >= scene.tList.size()) {
				tIndex = scene.tList.size()-1;
			}
			currentScene = scene;
			loadImage(scene.tList.get(tIndex));
			repaintMap();
		} 
		catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	@Override
	public void mousePressed(MouseEvent e) {}
	@Override
	public void mouseReleased(MouseEvent e) {}
	@Override
	public void mouseEntered(MouseEvent e) {}
	@Override
	public void mouseExited(MouseEvent e) {}
	
	private void log(String s) {
		if (logArea != null) logArea.append(s + "\n");
		System.out.println(s);
	}
	
	private static void exit(String s) {
		System.out.println(s);
		System.exit(1);
	}
	
	public static void main(String[] args) throws Exception {
		boolean setDefaults = true;
		for (int i = 0; i < args.length; i++) { // to change settings easily from Eclipse
			if (args[i].equals("-no-defaults")) setDefaults = false;
		}
		
		FmowVisualizer v = new FmowVisualizer();
		v.hasGui = true;
		int w = 1500;
		
		if (setDefaults) {
			v.createTOC = false;
			v.hasGui = true;
			w = 1500;
			v.solutionPath = null;
			v.dataDir = null;
		}
		else {
			// These are just some default settings for local testing, can be ignored.
			
			// sample data
//			v.dataDir = "../data/aws/fmow-rgb/train/";
//			v.solutionPath = "../data/aws/fmow-rgb/sample-solution.txt";
			
			v.dataDir = "c:/tmp/topcoder-fmow/0912";
			
			// training data
			
			// test data
			
			// validation
			
		}
		
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-toc")) v.createTOC = true;
			if (args[i].equals("-no-gui")) v.hasGui = false;
			if (args[i].equals("-w")) w = Integer.parseInt(args[i+1]);
			if (args[i].equals("-solution")) v.solutionPath = args[i+1];
			if (args[i].equals("-data-dir")) v.dataDir = args[i+1];
			if (args[i].equals("-no-ms")) v.useMsData = false;
			if (args[i].equals("-max-per-cat")) v.maxNperCategory = Integer.parseInt(args[i+1]);
			if (args[i].equals("-scene-filter")) v.sceneFilter = args[i+1];
		}
		
		if (v.dataDir == null) {
			exit("Data folder not set, use -data-dir");
		}
		
		if (v.createTOC) {
			v.log("Creating TOC file, ignoring all other settings");
			v.hasGui = false;
			v.solutionPath = null;
			v.maxNperCategory = Integer.MAX_VALUE;
			v.sceneFilter = null;
		}		
		
		v.setupGUI(w);
		v.run();
	}
}
