/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2.ui.coverage2;

import ucar.nc2.ft2.coverage.*;
import ucar.nc2.ui.util.NamedObjects;
import ucar.ui.event.ActionCoordinator;
import ucar.ui.event.ActionSourceListener;
import ucar.ui.event.ActionValueEvent;
import ucar.nc2.ui.geoloc.*;
import ucar.nc2.ui.gis.MapBean;
import ucar.nc2.ui.grid.*;
import ucar.ui.widget.*;
import ucar.ui.util.NamedObject;
import ucar.unidata.geoloc.Projection;
import ucar.unidata.geoloc.ProjectionRect;
import ucar.unidata.geoloc.projection.LatLonProjection;
import ucar.util.prefs.PreferencesExt;
import ucar.ui.prefs.Debug;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JToolBar;
import javax.swing.RootPaneContainer;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EtchedBorder;

/**
 * ft2.coverage widget for displaying.
 * more or less the controller in MVC
 */
public class CoverageViewer extends JPanel {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CoverageViewer.class);

  // constants
  private static final int DELAY_DRAW_AFTER_DATA_EVENT = 250; // quarter sec
  private static final String LastMapAreaName = "LastMapArea";
  private static final String LastProjectionName = "LastProjection";
  private static final String ColorScaleName = "ColorScale";

  private final PreferencesExt store;

  // UI components
  private ColorScale colorScale;
  private ColorScale.Panel colorScalePanel;
  private List<Chooser> choosers;
  private SuperComboBox fieldChooser, levelChooser, timeChooser, ensembleChooser, runtimeChooser;
  private JLabel dataValueLabel;

  private NavigatedPanel navPanel;

  // UI components that need global scope
  private TextHistoryPane datasetInfoTA;
  private JPanel drawingPanel;
  private JComboBox csDataMinMax;
  private PopupMenu mapBeanMenu;
  private JSpinner strideSpinner;

  private final JLabel datasetNameLabel = new JLabel();

  // the various managers and dialog boxes
  private ProjectionManager projManager;
  private IndependentWindow infoWindow;

  // toolbars
  private JPanel fieldPanel, toolPanel;
  private JToolBar navToolbar, moveToolbar;
  private AbstractAction navToolbarAction, moveToolbarAction;

  // actions
  private AbstractAction redrawAction;
  private AbstractAction showDatasetInfoAction;
  private AbstractAction minmaxHorizAction, minmaxLogAction, minmaxHoldAction;
  private AbstractAction fieldLoopAction, levelLoopAction, timeLoopAction, runtimeLoopAction;
  private AbstractAction chooseProjectionAction, saveCurrentProjectionAction;

  private AbstractAction dataProjectionAction, drawBBAction, showGridAction, showContoursAction,
      showContourLabelsAction;
  private AbstractAction drawHorizAction, drawVertAction;

  // data components
  private DataState dataState;
  private CoverageCollection coverageDataset;
  private Coverage currentField;
  private Projection project;

  // state
  private List<NamedObject> levelNames, timeNames, ensembleNames, runtimeNames;
  private int currentLevel;
  private int currentTime;
  private int currentEnsemble;
  private int currentRunTime;
  private boolean drawHorizOn = true, drawVertOn;
  private boolean eventsOK = true;
  private final Color mapColor = Color.black;
  private int mapBeanCount;

  // rendering
  private final AffineTransform atI = new AffineTransform(); // identity transform
  private ucar.nc2.ui.util.Renderer mapRenderer;
  private CoverageRenderer coverageRenderer;
  private javax.swing.Timer redrawTimer;

  public CoverageViewer(PreferencesExt pstore, RootPaneContainer root, FileManager fileChooser, int defaultHeight) {
    this.store = pstore;

    try {
      // choosers
      choosers = new ArrayList<>();
      fieldChooser = new SuperComboBox(root, "field", true, null);
      choosers.add(new Chooser("field", fieldChooser, true));
      levelChooser = new SuperComboBox(root, "level", false, null);
      choosers.add(new Chooser("level", levelChooser, false));
      timeChooser = new SuperComboBox(root, "time", false, null);
      choosers.add(new Chooser("time", timeChooser, false));
      ensembleChooser = new SuperComboBox(root, "ensemble", false, null);
      choosers.add(new Chooser("ensemble", ensembleChooser, false));
      runtimeChooser = new SuperComboBox(root, "runtime", false, null);
      choosers.add(new Chooser("runtime", runtimeChooser, false));

      // gridTable
      // gridTable = new GridTable("field");
      // gtWindow = new IndependentWindow("Grid Table Information", BAMutil.getImage( "GDVs"), gridTable.getPanel());

      // PreferencesExt dsNode = (PreferencesExt) pstore.node("DatasetTable");
      // dsTable = new GeoGridTable(dsNode, true);
      // dsDialog = dsTable.makeDialog(root, "NetcdfDataset Info", false);
      // dsDialog.setIconImage( BAMutil.getImage( "GDVs"));
      // Rectangle bounds = (Rectangle) dsNode.getBean("DialogBounds", new Rectangle(50, 50, 800, 450));
      // dsDialog.setBounds( bounds);

      // colorscale
      Object bean = store.getBean(ColorScaleName, null);
      if (!(bean instanceof ColorScale))
        colorScale = new ColorScale("default");
      else
        colorScale = (ColorScale) store.getBean(ColorScaleName, null);

      colorScalePanel = new ColorScale.Panel(this, colorScale);
      csDataMinMax = new JComboBox(ColorScale.MinMaxType.values());
      csDataMinMax.setToolTipText("ColorScale Min/Max setting");
      csDataMinMax.addActionListener(e -> {
        coverageRenderer.setDataMinMaxType((ColorScale.MinMaxType) csDataMinMax.getSelectedItem());
        redrawLater();
      });

      // renderer
      // set up the renderers; Maps are added by addMapBean()
      coverageRenderer = new CoverageRenderer(store);
      coverageRenderer.setColorScale(colorScale);

      strideSpinner = new JSpinner(new SpinnerNumberModel(1, 1, 100, 1));
      strideSpinner.addChangeListener(e -> {
        Integer val = (Integer) strideSpinner.getValue();
        coverageRenderer.setHorizStride(val);
      });

      makeActionsDataset();
      makeActionsToolbars();
      makeActions();
      makeEventManagement();

      //// toolPanel
      toolPanel = new JPanel();
      toolPanel.setBorder(new EtchedBorder());
      toolPanel.setLayout(new MFlowLayout(FlowLayout.LEFT, 0, 0));

      // menus
      JMenu dataMenu = new JMenu("Dataset");
      dataMenu.setMnemonic('D');
      JMenu configMenu = new JMenu("Configure");
      configMenu.setMnemonic('C');
      JMenu toolMenu = new JMenu("Controls");
      toolMenu.setMnemonic('T');
      JMenuBar menuBar = new JMenuBar();
      menuBar.add(dataMenu);
      menuBar.add(configMenu);
      menuBar.add(toolMenu);
      toolPanel.add(menuBar);

      // field chooser panel - delay adding the choosers
      fieldPanel = new JPanel();
      fieldPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
      toolPanel.add(fieldPanel);

      // stride
      toolPanel.add(strideSpinner);

      // buttcons
      BAMutil.addActionToContainer(toolPanel, drawHorizAction);
      BAMutil.addActionToContainer(toolPanel, drawVertAction);
      mapBeanMenu = MapBean.makeMapSelectButton();
      toolPanel.add(mapBeanMenu.getParentComponent());

      // the Navigated panel and its toolbars
      navPanel = new NavigatedPanel();
      navPanel.setLayout(new FlowLayout());
      ProjectionRect ma = (ProjectionRect) store.getBean(LastMapAreaName, null);
      if (ma != null)
        navPanel.setMapArea(ma);

      navToolbar = navPanel.getNavToolBar();
      moveToolbar = navPanel.getMoveToolBar();
      if ((Boolean) navToolbarAction.getValue(BAMutil.STATE))
        toolPanel.add(navToolbar);
      if ((Boolean) moveToolbarAction.getValue(BAMutil.STATE))
        toolPanel.add(moveToolbar);
      makeNavPanelWiring();
      addActionsToMenus(dataMenu, configMenu, toolMenu);

      BAMutil.addActionToContainer(toolPanel, navPanel.setReferenceAction);
      BAMutil.addActionToContainer(toolPanel, dataProjectionAction);
      BAMutil.addActionToContainer(toolPanel, showGridAction);
      BAMutil.addActionToContainer(toolPanel, drawBBAction);
      // BAMutil.addActionToContainer(toolPanel, showContourLabelsAction);
      BAMutil.addActionToContainer(toolPanel, redrawAction);

      // vertical split
      // vertPanel = new VertPanel();
      // splitDraw = new JSplitPane(JSplitPane.VERTICAL_SPLIT, panz, vertPanel);
      // int divLoc = store.getInt( "vertSplit", 2*defaultHeight/3);
      // splitDraw.setDividerLocation(divLoc);
      drawingPanel = new JPanel(new BorderLayout()); // filled later

      // status panel
      JPanel statusPanel = new JPanel(new BorderLayout());
      statusPanel.setBorder(new EtchedBorder());
      JLabel positionLabel = new JLabel("position");
      positionLabel.setToolTipText("position at cursor");
      dataValueLabel = new JLabel("data value", SwingConstants.CENTER);
      dataValueLabel.setToolTipText("data value (double click on grid)");
      statusPanel.add(positionLabel, BorderLayout.WEST);
      statusPanel.add(dataValueLabel, BorderLayout.CENTER);
      navPanel.setPositionLabel(positionLabel);

      // assemble
      JPanel westPanel = new JPanel(new BorderLayout());
      westPanel.add(colorScalePanel, BorderLayout.CENTER);
      westPanel.add(csDataMinMax, BorderLayout.NORTH);

      JPanel northPanel = new JPanel();
      // northPanel.setLayout( new BoxLayout(northPanel, BoxLayout.Y_AXIS));
      northPanel.setLayout(new BorderLayout());
      northPanel.add(datasetNameLabel, BorderLayout.NORTH);
      northPanel.add(toolPanel, BorderLayout.SOUTH);

      setLayout(new BorderLayout());
      add(northPanel, BorderLayout.NORTH);
      add(statusPanel, BorderLayout.SOUTH);
      add(westPanel, BorderLayout.WEST);
      add(drawingPanel, BorderLayout.CENTER);

      setDrawHorizAndVert(drawHorizOn, drawVertOn);

      // get last saved Projection
      project = (Projection) store.getBean(LastProjectionName, null);
      if (project != null) {
        setProjection(project);
      }

      // redraw timer
      redrawTimer = new javax.swing.Timer(0, e -> {
        // invoke in event thread
        SwingUtilities.invokeLater(() -> draw(false));
        redrawTimer.stop(); // one-shot timer
      });
      redrawTimer.setInitialDelay(DELAY_DRAW_AFTER_DATA_EVENT);
      redrawTimer.setRepeats(false);


    } catch (Exception e) {
      System.out.println("UI creation failed");
      e.printStackTrace();
    }
  }


  // actions that control the dataset
  private void makeActionsDataset() {

    /*
     * choose local dataset
     * AbstractAction chooseLocalDatasetAction = new AbstractAction() {
     * public void actionPerformed(ActionEvent e) {
     * String filename = fileChooser.chooseFilename();
     * if (filename == null) return;
     * 
     * Dataset invDs;
     * try { // DatasetNode parent, String name, Map<String, Object> flds, List< AccessBuilder > accessBuilders, List<
     * DatasetBuilder > datasetBuilders
     * Map<String, Object> flds = new HashMap<>();
     * flds.put(Dataset.FeatureType, FeatureType.GRID.toString());
     * flds.put(Dataset.ServiceName, ServiceType.File.toString()); // bogus
     * invDs = new Dataset(null, filename, flds, null, null);
     * setDataset(invDs);
     * 
     * } catch (Exception ue) {
     * JOptionPane.showMessageDialog(CoverageDisplay.this, "Invalid filename = <" + filename + ">\n" + ue.getMessage());
     * ue.printStackTrace();
     * }
     * }
     * };
     * BAMutil.setActionProperties(chooseLocalDatasetAction, "FileChooser", "open Local dataset...", false, 'L', -1);
     * 
     * /* saveDatasetAction = new AbstractAction() {
     * public void actionPerformed(ActionEvent e) {
     * String fname = controller.getDatasetName();
     * if (fname != null) {
     * savedDatasetList.add( fname);
     * BAMutil.addActionToMenu( savedDatasetMenu, new DatasetAction( fname), 0);
     * }
     * }
     * };
     * BAMutil.setActionProperties( saveDatasetAction, null, "save dataset", false, 'S', 0);
     */

    // Configure
    chooseProjectionAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        getProjectionManager().setVisible();
      }
    };
    BAMutil.setActionProperties(chooseProjectionAction, null, "Projection Manager...", false, 'P', 0);

    saveCurrentProjectionAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        getProjectionManager();
        // set the bounding box
        // Projection proj = navPanel.getProjectionImpl().constructCopy();
        // proj.setDefaultMapArea(navPanel.getMapArea());
        // if (debug) System.out.println(" GV save projection "+ proj);

        // projManage.setMap(renderAll.get("Map")); LOOK!
        // projManager.saveProjection( proj);
      }
    };
    BAMutil.setActionProperties(saveCurrentProjectionAction, null, "save Current Projection", false, 'S', 0);

    /*
     * chooseColorScaleAction = new AbstractAction() {
     * public void actionPerformed(ActionEvent e) {
     * if (null == csManager) // lazy instantiation
     * makeColorScaleManager();
     * csManager.show();
     * }
     * };
     * BAMutil.setActionProperties( chooseColorScaleAction, null, "ColorScale Manager...", false, 'C', 0);
     * 
     */
    // redraw
    redrawAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        repaint();
        start(true);
        draw(true);
      }
    };
    BAMutil.setActionProperties(redrawAction, "alien", "RedRaw", false, 'W', 0);

    showDatasetInfoAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        if (infoWindow == null) {
          datasetInfoTA = new TextHistoryPane();
          infoWindow = new IndependentWindow("Dataset Information", BAMutil.getImage("nj22/GDVs"), datasetInfoTA);
          infoWindow.setSize(700, 700);
          infoWindow.setLocation(100, 100);
        }

        datasetInfoTA.clear();
        if (coverageDataset != null) {
          Formatter f = new Formatter();
          coverageDataset.toString(f);
          datasetInfoTA.appendLine(f.toString());
        } else {
          datasetInfoTA.appendLine("No coverageDataset loaded");
        }
        datasetInfoTA.gotoTop();
        infoWindow.show();
      }
    };
    BAMutil.setActionProperties(showDatasetInfoAction, "Information", "Show info...", false, 'S', -1);

    /*
     * showNcMLAction = new AbstractAction() {
     * public void actionPerformed(ActionEvent e) {
     * if (ncmlWindow == null) {
     * ncmlTA = new TextHistoryPane();
     * ncmlWindow = new IndependentWindow("Dataset NcML", BAMutil.getImage( "GDVs"), ncmlTA);
     * ncmlWindow.setSize(700,700);
     * ncmlWindow.setLocation(200, 70);
     * }
     * 
     * ncmlTA.clear();
     * //datasetInfoTA.appendLine( "GeoGrid XML for "+ controller.getDatasetName()+"\n");
     * ncmlTA.appendLine( controller.getNcML());
     * ncmlTA.gotoTop();
     * ncmlWindow.show();
     * }
     * };
     * BAMutil.setActionProperties( showNcMLAction, null, "Show NcML...", false, 'X', -1);
     */

    /*
     * showGridDatasetInfoAction = new AbstractAction() {
     * public void actionPerformed(ActionEvent e) {
     * if (ncmlWindow == null) {
     * ncmlTA = new TextHistoryPane();
     * ncmlWindow = new IndependentWindow("Dataset NcML", BAMutil.getImage( "GDVs"), ncmlTA);
     * ncmlWindow.setSize(700,700);
     * ncmlWindow.setLocation(200, 70);
     * }
     * 
     * ncmlTA.clear();
     * //datasetInfoTA.appendLine( "GeoGrid XML for "+ controller.getDatasetName()+"\n");
     * ncmlTA.appendLine( controller.getDatasetXML());
     * ncmlTA.gotoTop();
     * ncmlWindow.show();
     * }
     * };
     * BAMutil.setActionProperties( showGridDatasetInfoAction, null, "Show GridDataset Info XML...", false, 'X', -1);
     * 
     * // show netcdf dataset Table
     * /* showNetcdfDatasetAction = new AbstractAction() {
     * public void actionPerformed(ActionEvent e) {
     * NetcdfDataset netcdfDataset = controller.getNetcdfDataset();
     * if (null != netcdfDataset) {
     * try {
     * dsTable.setDataset(netcdfDataset, null);
     * } catch (IOException e1) {
     * e1.printStackTrace();
     * return;
     * }
     * dsDialog.show();
     * }
     * }
     * };
     * BAMutil.setActionProperties( showNetcdfDatasetAction, "netcdf", "NetcdfDataset Table Info...", false, 'D', -1);
     */

    minmaxHorizAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        csDataMinMax.setSelectedItem(ColorScale.MinMaxType.horiz);
        setDataMinMaxType(ColorScale.MinMaxType.horiz);
      }
    };
    BAMutil.setActionProperties(minmaxHorizAction, null, "Horizontal plane", false, 'H', 0);

    minmaxLogAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        csDataMinMax.setSelectedItem(ColorScale.MinMaxType.log);
        setDataMinMaxType(ColorScale.MinMaxType.log);
      }
    };
    BAMutil.setActionProperties(minmaxLogAction, null, "log horiz plane", false, 'V', 0);

    /*
     * minmaxVolAction = new AbstractAction() {
     * public void actionPerformed(ActionEvent e) {
     * csDataMinMax.setSelectedIndex(GridRenderer.VOL_MinMaxType);
     * controller.setDataMinMaxType(GridRenderer.MinMaxType.vert;
     * }
     * };
     * BAMutil.setActionProperties( minmaxVolAction, null, "Grid volume", false, 'G', 0);
     */

    minmaxHoldAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        csDataMinMax.setSelectedItem(ColorScale.MinMaxType.hold);
        setDataMinMaxType(ColorScale.MinMaxType.hold);
      }
    };
    BAMutil.setActionProperties(minmaxHoldAction, null, "Hold scale constant", false, 'C', 0);

    fieldLoopAction = new LoopControlAction(fieldChooser);
    levelLoopAction = new LoopControlAction(levelChooser);
    timeLoopAction = new LoopControlAction(timeChooser);
    runtimeLoopAction = new LoopControlAction(runtimeChooser);
  }

  private void makeActionsToolbars() {

    navToolbarAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Boolean state = (Boolean) getValue(BAMutil.STATE);
        if (state)
          toolPanel.add(navToolbar);
        else
          toolPanel.remove(navToolbar);
      }
    };
    BAMutil.setActionProperties(navToolbarAction, "MagnifyPlus", "show Navigate toolbar", true, 'M', 0);
    navToolbarAction.putValue(BAMutil.STATE, store.getBoolean("navToolbarAction", true));

    moveToolbarAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Boolean state = (Boolean) getValue(BAMutil.STATE);
        if (state)
          toolPanel.add(moveToolbar);
        else
          toolPanel.remove(moveToolbar);
      }
    };
    BAMutil.setActionProperties(moveToolbarAction, "Up", "show Move toolbar", true, 'M', 0);
    moveToolbarAction.putValue(BAMutil.STATE, store.getBoolean("moveToolbarAction", true));
  }

  // create all actions here
  // the actions can then be attached to buttcons, menus, etc
  private void makeActions() {
    boolean state;

    dataProjectionAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Boolean state = (Boolean) getValue(BAMutil.STATE);
        if (state) {
          Projection dataProjection = coverageRenderer.getDataProjection();
          if (null != dataProjection)
            setProjection(dataProjection);
        } else {
          setProjection(new LatLonProjection());
        }
      }
    };
    BAMutil.setActionProperties(dataProjectionAction, "nj22/DataProjection", "use Data Projection", true, 'D', 0);
    dataProjectionAction.putValue(BAMutil.STATE, true);

    // contouring
    drawBBAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Boolean state = (Boolean) getValue(BAMutil.STATE);
        coverageRenderer.setDrawBB(state);
        draw(false);
      }
    };
    BAMutil.setActionProperties(drawBBAction, "nj22/Contours", "draw bounding box", true, 'B', 0);
    drawBBAction.putValue(BAMutil.STATE, false);

    // draw horiz
    drawHorizAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        drawHorizOn = (Boolean) getValue(BAMutil.STATE);
        setDrawHorizAndVert(drawHorizOn, drawVertOn);
        draw(false);
      }
    };
    BAMutil.setActionProperties(drawHorizAction, "nj22/DrawHoriz", "draw horizontal", true, 'H', 0);
    state = store.getBoolean("drawHorizAction", true);
    drawHorizAction.putValue(BAMutil.STATE, state);
    drawHorizOn = state;

    // draw Vert
    drawVertAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        drawVertOn = (Boolean) getValue(BAMutil.STATE);
        setDrawHorizAndVert(drawHorizOn, drawVertOn);
        draw(false);
      }
    };
    BAMutil.setActionProperties(drawVertAction, "nj22/DrawVert", "draw vertical", true, 'V', 0);
    state = store.getBoolean("drawVertAction", false);
    drawVertAction.putValue(BAMutil.STATE, state);
    drawVertOn = state;

    // show grid
    showGridAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Boolean state = (Boolean) getValue(BAMutil.STATE);
        coverageRenderer.setDrawGridLines(state);
        draw(false);
      }
    };
    BAMutil.setActionProperties(showGridAction, "nj22/Grid", "show grid lines", true, 'G', 0);
    state = store.getBoolean("showGridAction", false);
    showGridAction.putValue(BAMutil.STATE, state);
    coverageRenderer.setDrawGridLines(state);

    // contouring
    showContoursAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Boolean state = (Boolean) getValue(BAMutil.STATE);
        coverageRenderer.setDrawContours(state);
        draw(false);
      }
    };
    BAMutil.setActionProperties(showContoursAction, "nj22/Contours", "show contours", true, 'C', 0);
    state = store.getBoolean("showContoursAction", false);
    showContoursAction.putValue(BAMutil.STATE, state);
    coverageRenderer.setDrawContours(state);

    // contouring labels
    showContourLabelsAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        Boolean state = (Boolean) getValue(BAMutil.STATE);
        coverageRenderer.setDrawContourLabels(state);
        draw(false);
      }
    };
    BAMutil.setActionProperties(showContourLabelsAction, "nj22/ContourLabels", "show contour labels", true, 'L', 0);
    state = store.getBoolean("showContourLabelsAction", false);
    showContourLabelsAction.putValue(BAMutil.STATE, state);
    coverageRenderer.setDrawContourLabels(state);
  }

  private void makeEventManagement() {
    // LOOK what prevents these ActionCoordinator from getting GC ?
    //// manage field selection events
    String actionName = "field";
    ActionCoordinator fieldCoordinator = new ActionCoordinator(actionName);
    // connect to the fieldChooser
    fieldCoordinator.addActionSourceListener(fieldChooser.getActionSourceListener());
    // connect to the gridTable
    // fieldCoordinator.addActionSourceListener(gridTable.getActionSourceListener());
    // heres what to do when the currentField changes
    ActionSourceListener fieldSource = new ActionSourceListener(actionName) {
      public void actionPerformed(ActionValueEvent e) {
        if (setField(e.getValue())) {
          if (e.getActionCommand().equals("redrawImmediate")) {
            draw(true);
            // colorScalePanel.paintImmediately(colorScalePanel.getBounds()); // kludgerino
          } else
            redrawLater();
        }
      }
    };
    fieldCoordinator.addActionSourceListener(fieldSource);

    //// manage level selection events
    actionName = "level";
    ActionCoordinator levelCoordinator = new ActionCoordinator(actionName);
    levelCoordinator.addActionSourceListener(levelChooser.getActionSourceListener());
    // connect to the vertPanel
    /*
     * levelCoordinator.addActionSourceListener(vertPanel.getActionSourceListener());
     * // also manage Pick events from the vertPanel
     * vertPanel.getDrawArea().addPickEventListener( new PickEventListener() {
     * public void actionPerformed(PickEvent e) {
     * int level = renderGrid.findLevelCoordElement(e.getLocation().getY());
     * if ((level != -1) && (level != currentLevel)) {
     * currentLevel = level;
     * redrawLater();
     * String selectedName = levelNames.get(currentLevel).getName();
     * if (Debug.isSet("pick/event"))
     * System.out.println("pick.event Vert: "+selectedName);
     * levelSource.fireActionValueEvent(ActionSourceListener.SELECTED, selectedName);
     * }
     * }
     * });
     */
    // heres what to do when a level changes
    ActionSourceListener levelSource = new ActionSourceListener(actionName) {
      public void actionPerformed(ActionValueEvent e) {
        int level = findIndexFromName(levelNames, e.getValue().toString());
        if ((level != -1) && (level != currentLevel)) {
          currentLevel = level;
          if (e.getActionCommand().equals("redrawImmediate")) {
            draw(true);
          } else
            redrawLater();
        }
      }
    };
    levelCoordinator.addActionSourceListener(levelSource);

    //// manage time selection events
    actionName = "time";
    ActionCoordinator timeCoordinator = new ActionCoordinator(actionName);
    timeCoordinator.addActionSourceListener(timeChooser.getActionSourceListener());
    // heres what to do when the time changes
    ActionSourceListener timeSource = new ActionSourceListener(actionName) {
      public void actionPerformed(ActionValueEvent e) {
        int time = findIndexFromName(timeNames, e.getValue().toString());
        if ((time != -1) && (time != currentTime)) {
          currentTime = time;
          if (e.getActionCommand().equals("redrawImmediate")) {
            draw(true);
            // colorScalePanel.paintImmediately(colorScalePanel.getBounds()); // kludgerino
          } else
            redrawLater();
        }
      }
    };
    timeCoordinator.addActionSourceListener(timeSource);

    // manage runtime selection events
    actionName = "runtime";
    ActionCoordinator runtimeCoordinator = new ActionCoordinator(actionName);
    runtimeCoordinator.addActionSourceListener(runtimeChooser.getActionSourceListener());
    // heres what to do when the runtime changes
    ActionSourceListener runtimeSource = new ActionSourceListener(actionName) {
      public void actionPerformed(ActionValueEvent e) {
        // Object dataValue = e.getValue();
        int runtime = findIndexFromName(runtimeNames, e.getValue().toString());
        if ((runtime != -1) && (runtime != currentRunTime)) {
          currentRunTime = runtime;

          if (dataState.taxis2D != null) {
            // CoverageCoordAxis1D taxis = dataState.taxis2D.getTimeAxisForRun((CalendarDate) dataValue);
            dataState.taxis = dataState.taxis2D.getTimeAxisForRun(currentRunTime);
            timeNames = NamedObjects.getNames(dataState.taxis);
            timeChooser.setCollection(timeNames.iterator());
            if (currentTime >= timeNames.size())
              currentTime = 0;
            timeChooser.setSelectedByIndex(currentTime);
          }

          if (e.getActionCommand().equals("redrawImmediate")) {
            draw(true);
          } else
            redrawLater();
        }
      }
    };
    runtimeCoordinator.addActionSourceListener(runtimeSource);

    //// manage runtime selection events
    actionName = "ensemble";
    ActionCoordinator ensembleCoordinator = new ActionCoordinator(actionName);
    ensembleCoordinator.addActionSourceListener(ensembleChooser.getActionSourceListener());
    // heres what to do when the time changes
    ActionSourceListener ensembleSource = new ActionSourceListener(actionName) {
      public void actionPerformed(ActionValueEvent e) {
        int ensIndex = findIndexFromName(ensembleNames, e.getValue().toString());
        if ((ensIndex != -1) && (ensIndex != currentEnsemble)) {
          currentEnsemble = ensIndex;
          if (e.getActionCommand().equals("redrawImmediate")) {
            draw(true);
          } else
            redrawLater();
        }
      }
    };
    ensembleCoordinator.addActionSourceListener(ensembleSource);
  }

  private void makeNavPanelWiring() {

    // get Projection Events from the navigated panel
    navPanel.addNewProjectionListener(e -> {
      if (Debug.isSet("event/NewProjection"))
        System.out.println("Controller got NewProjectionEvent " + navPanel.getMapArea());
      if (eventsOK && mapRenderer != null) {
        mapRenderer.setProjection(e.getProjection());
        coverageRenderer.setViewProjection(e.getProjection());
        drawH(false);
      }
    });

    // get NewMapAreaEvents from the navigated panel
    navPanel.addNewMapAreaListener(e -> {
      if (Debug.isSet("event/NewMapArea"))
        System.out.println("Controller got NewMapAreaEvent " + navPanel.getMapArea());
      drawH(false);
    });

    // get Move events from the navigated panel
    navPanel.addCursorMoveEventListener(e -> {
      String valueS = coverageRenderer.getXYvalueStr(e.getLocation());
      dataValueLabel.setText(valueS);
    });

  }

  private int findIndexFromName(List<NamedObject> list, String name) {
    for (int idx = 0; idx < list.size(); idx++) {
      NamedObject no = list.get(idx);
      if (name.equals(no.getName()))
        return idx;
    }
    log.error("findIndexFromName cant find " + name);
    return -1;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////

  public void save() {
    // store.putInt( "vertSplit", splitDraw.getDividerLocation());

    store.putBoolean("navToolbarAction", (Boolean) navToolbarAction.getValue(BAMutil.STATE));
    store.putBoolean("moveToolbarAction", (Boolean) moveToolbarAction.getValue(BAMutil.STATE));

    if (projManager != null)
      projManager.storePersistentData();

    store.putBeanObject(LastMapAreaName, navPanel.getMapArea());
    store.putBeanObject(LastProjectionName, navPanel.getProjectionImpl());
    // if (gridDataset != null)
    // store.put(LastDatasetName, gridDataset.getTitle());
    store.putBeanObject(ColorScaleName, colorScale);

    store.putBoolean("showGridAction", (Boolean) showGridAction.getValue(BAMutil.STATE));
    store.putBoolean("showContoursAction", (Boolean) showContoursAction.getValue(BAMutil.STATE));
    store.putBoolean("showContourLabelsAction", (Boolean) showContourLabelsAction.getValue(BAMutil.STATE));

  }

  // add a MapBean to the User Interface
  public void addMapBean(MapBean mb) {
    mapBeanMenu.addAction(mb.getActionDesc(), mb.getIcon(), mb.getAction());

    // first one is the "default"
    if (mapBeanCount == 0) {
      setMapRenderer(mb.getRenderer());
    }
    mapBeanCount++;

    mb.addPropertyChangeListener(e -> {
      if (e.getPropertyName().equals("Renderer")) {
        setMapRenderer((ucar.nc2.ui.util.Renderer) e.getNewValue());
      }
    });
  }

  void setMapRenderer(ucar.nc2.ui.util.Renderer mapRenderer) {
    this.mapRenderer = mapRenderer;
    mapRenderer.setProjection(navPanel.getProjectionImpl());
    mapRenderer.setColor(mapColor);
    redrawLater();
  }

  // assume that its done in the event thread
  boolean showDataset() {
    // temp kludge for initialization
    Iterable<Coverage> grids = coverageDataset.getCoverages();

    currentField = grids.iterator().next(); // first
    currentLevel = 0;
    currentTime = 0;
    currentEnsemble = 0;
    currentRunTime = 0;

    eventsOK = false; // dont let this trigger redraw
    this.dataState = coverageRenderer.setCoverage(coverageDataset, currentField);
    coverageRenderer.setDataProjection(currentField.getCoordSys().getProjection());
    setField(currentField);

    // LOOK if possible, change the projection and the map area to one that fits this dataset
    Projection dataProjection = currentField.getCoordSys().getProjection();
    if (dataProjection != null) {
      setProjection(dataProjection);
    }
    ProjectionRect fieldBB = currentField.getBoundingBox();
    if (fieldBB != null) {
      navPanel.setMapArea(fieldBB);
    }

    // events now ok
    eventsOK = true;
    return true;
  }

  public void setDataMinMaxType(ColorScale.MinMaxType type) {
    coverageRenderer.setDataMinMaxType(type);
    redrawLater();
  }

  private boolean startOK = true;

  public void setDataset(ucar.nc2.ui.coverage2.CoverageTable dsTable) {
    this.coverageDataset = dsTable.getCoverageDataset();
    setFieldsFromBeans(dsTable.getCoverageBeans());

    startOK = false; // wait till redraw is hit before drawing
    showDataset();
    datasetNameLabel.setText("Dataset:  " + coverageDataset.getName());
    // gridTable.setDataset(controller.getFields());
  }

  public void setDataset(CoverageCollection gcd) {
    this.coverageDataset = gcd;
    setFields(gcd.getCoverages());

    startOK = false; // wait till redraw is hit before drawing
    showDataset();
    datasetNameLabel.setText("Dataset:  " + coverageDataset.getName());
    // gridTable.setDataset(controller.getFields());
  }

  void setFieldsFromBeans(List<CoverageTable.CoverageBean> fields) {
    fieldChooser.setCollection(fields.iterator());
  }

  void setFields(Iterable<Coverage> fields) {
    fieldChooser.setCollection(fields.iterator());
  }

  private boolean setField(Object fld) {
    Coverage gg = null;
    if (fld instanceof Coverage)
      gg = (Coverage) fld;
    else if (fld instanceof String)
      gg = coverageDataset.findCoverage((String) fld);
    else if (fld instanceof NamedObject)
      gg = coverageDataset.findCoverage(((NamedObject) fld).getName());
    if (null == gg)
      return false;

    this.dataState = coverageRenderer.setCoverage(coverageDataset, gg);
    coverageRenderer.setDataProjection(this.dataState.geocs.getProjection());
    currentField = gg;

    // set runtimes
    if (this.dataState.rtaxis != null) {
      runtimeNames = NamedObjects.getNames(this.dataState.rtaxis);
      currentRunTime = !runtimeNames.isEmpty() ? 0 : -1;
      if ((currentRunTime < 0) || (currentRunTime >= runtimeNames.size()))
        currentRunTime = 0;

      setChooserWanted("runtime", true);
      runtimeChooser.setCollection(runtimeNames.iterator(), true);
      NamedObject no = runtimeNames.get(currentRunTime);
      runtimeChooser.setSelectedByName(no.getName());

      if (this.dataState.taxis2D != null) {
        this.dataState.taxis = this.dataState.taxis2D.getTimeAxisForRun(currentRunTime);
      }

    } else {
      runtimeNames = new ArrayList<>();
      setChooserWanted("runtime", false);
      coverageRenderer.setRunTime(-1);
    }

    // set times
    boolean hasDependentTimeAxis;
    if (this.dataState.taxis != null) {
      timeNames = NamedObjects.getNames(this.dataState.taxis);
      if ((currentTime < 0) || (currentTime >= timeNames.size()))
        currentTime = 0;
      hasDependentTimeAxis = true;

      setChooserWanted("time", true);
      timeChooser.setCollection(timeNames.iterator(), true);
      NamedObject no = timeNames.get(currentTime);
      timeChooser.setSelectedByName(no.getName());

    } else {
      timeNames = new ArrayList<>();
      hasDependentTimeAxis = false;
      setChooserWanted("time", false);
    }

    // set ensembles
    if (this.dataState.ensaxis != null) {
      ensembleNames = NamedObjects.getNames(this.dataState.ensaxis);
      currentEnsemble = !ensembleNames.isEmpty() ? 0 : -1;
      if ((currentEnsemble < 0) || (currentEnsemble >= ensembleNames.size()))
        currentEnsemble = 0;

      setChooserWanted("ensemble", true);
      ensembleChooser.setCollection(ensembleNames.iterator(), true);
      NamedObject no = ensembleNames.get(currentEnsemble);
      ensembleChooser.setSelectedByName(no.getName());

    } else {
      ensembleNames = new ArrayList<>();
      setChooserWanted("ensemble", false);
      coverageRenderer.setEnsemble(-1);
    }

    // set levels
    if (this.dataState.zaxis != null) {
      levelNames = NamedObjects.getNames(this.dataState.zaxis);
      if ((currentLevel < 0) || (currentLevel >= levelNames.size()))
        currentLevel = 0;
      // vertPanel.setCoordSys(currentField.getCoordinateSystem(), currentLevel);

      setChooserWanted("level", true);
      levelChooser.setCollection(levelNames.iterator(), true);
      NamedObject no = levelNames.get(currentLevel);
      levelChooser.setSelectedByName(no.getName());

    } else {
      levelNames = new ArrayList<>();
      setChooserWanted("level", false);
      coverageRenderer.setLevel(-1);
    }

    addChoosers();

    fieldChooser.setToolTipText(gg.getShortName());
    colorScalePanel.setUnitString(gg.getUnitsString());
    return true;
  }

  void setDrawHorizAndVert(boolean drawHoriz, boolean drawVert) {
    drawingPanel.removeAll();
    if (drawHoriz && drawVert) {
      // splitDraw.setTopComponent(panz);
      // splitDraw.setBottomComponent(vertPanel);
      drawingPanel.add(navPanel, BorderLayout.CENTER);
    } else if (drawHoriz) {
      drawingPanel.add(navPanel, BorderLayout.CENTER);
    } else if (drawVert) {
      drawingPanel.add(navPanel, BorderLayout.CENTER); // LOOK drawVert not supported
    }
  }

  public void setProjection(Projection p) {
    project = p;
    if (mapRenderer != null)
      mapRenderer.setProjection(p);
    coverageRenderer.setViewProjection(p);
    // renderWind.setProjection( p);
    navPanel.setProjectionImpl(p);
    redrawLater();
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////

  void start(boolean ok) {
    startOK = ok;
  }

  synchronized void draw(boolean immediate) {
    if (!startOK)
      return;

    coverageRenderer.setLevel(currentLevel);
    coverageRenderer.setTime(currentTime);
    // renderGrid.setSlice(currentSlice);
    coverageRenderer.setEnsemble(currentEnsemble);
    coverageRenderer.setRunTime(currentRunTime);

    if (drawHorizOn)
      drawH(immediate);
    // if (drawVertOn)
    // drawV(immediate);
  }

  private void drawH(boolean immediate) {
    if (!startOK)
      return;

    // cancel any redrawLater
    boolean already = redrawTimer.isRunning();
    if (already)
      redrawTimer.stop();

    long tstart = System.currentTimeMillis();
    long startTime, tookTime;

    //// horizontal slice
    // the Navigated Panel's BufferedImage graphics
    Graphics2D gNP = navPanel.getBufferedImageGraphics();
    if (gNP == null) // panel not drawn on screen yet
      return;

    // clear
    gNP.setBackground(navPanel.getBackgroundColor());
    gNP.fill(gNP.getClipBounds());

    // draw grid
    startTime = System.currentTimeMillis();
    coverageRenderer.renderPlanView(gNP, atI);
    if (Debug.isSet("timing/GridDraw")) {
      tookTime = System.currentTimeMillis() - startTime;
      System.out.println("timing.GridDraw: " + tookTime * .001 + " seconds");
    }

    // draw Map
    if (mapRenderer != null) {
      startTime = System.currentTimeMillis();
      mapRenderer.draw(gNP, atI);
      if (Debug.isSet("timing/MapDraw")) {
        tookTime = System.currentTimeMillis() - startTime;
        System.out.println("timing/MapDraw: " + tookTime * .001 + " seconds");
      }
    }

    /*
     * draw Winds
     * if (drawWinds) {
     * startTime = System.currentTimeMillis();
     * renderWind.draw(gNP, currentLevel, currentTime);
     * if (Debug.isSet("timing/WindsDraw")) {
     * tookTime = System.currentTimeMillis() - startTime;
     * System.out.println("timing.WindsDraw: " + tookTime*.001 + " seconds");
     * }
     * }
     */

    // copy buffer to the screen
    if (immediate)
      navPanel.drawG();
    else
      navPanel.repaint();

    // cleanup
    gNP.dispose();

    if (Debug.isSet("timing/total")) {
      tookTime = System.currentTimeMillis() - tstart;
      System.out.println("timing.total: " + tookTime * .001 + " seconds");
    }
  }

  /*
   * private void drawV(boolean immediate) {
   * if (!startOK) return;
   * ScaledPanel drawArea = vertPanel.getDrawArea();
   * Graphics2D gV = drawArea.getBufferedImageGraphics();
   * if (gV == null)
   * return;
   * 
   * long startTime = System.currentTimeMillis();
   * 
   * gV.setBackground(Color.white);
   * gV.fill(gV.getClipBounds());
   * renderGrid.renderVertView(gV, atI);
   * 
   * if (Debug.isSet("timing/GridDrawVert")) {
   * long tookTime = System.currentTimeMillis() - startTime;
   * System.out.println("timing.GridDrawVert: " + tookTime*.001 + " seconds");
   * }
   * gV.dispose();
   * 
   * // copy buffer to the screen
   * if (immediate)
   * drawArea.drawNow();
   * else
   * drawArea.repaint();
   * }
   */

  private synchronized void redrawLater() {
    // redrawComplete |= complete;
    boolean already = redrawTimer.isRunning();
    if (already)
      redrawTimer.restart();
    else
      redrawTimer.start();
  }

  public ProjectionManager getProjectionManager() {
    if (null != projManager)
      return projManager;

    projManager = new ProjectionManager(null, store);
    projManager.addPropertyChangeListener(e -> {
      if (e.getPropertyName().equals("ProjectionImpl")) {
        Projection p = (Projection) e.getNewValue();
        setProjection(p);
      }
    });

    return projManager;
  }

  private void addChoosers() {
    fieldPanel.removeAll();
    for (Chooser c : choosers) {
      if (c.isWanted)
        fieldPanel.add(c.field);
    }
  }

  private static class Chooser {
    Chooser(String name, SuperComboBox field, boolean want) {
      this.name = name;
      this.field = field;
      this.isWanted = want;
    }

    boolean isWanted;
    String name;
    SuperComboBox field;
  }

  private void setChooserWanted(String name, boolean want) {
    for (Chooser chooser : choosers) {
      if (chooser.name.equals(name))
        chooser.isWanted = want;
    }
  }

  private void addActionsToMenus(JMenu datasetMenu, JMenu configMenu, JMenu toolMenu) {
    // Info
    BAMutil.addActionToMenu(datasetMenu, showDatasetInfoAction);

    /// Configure
    JMenu toolbarMenu = new JMenu("Toolbars");
    toolbarMenu.setMnemonic('T');
    configMenu.add(toolbarMenu);
    BAMutil.addActionToMenu(toolbarMenu, navToolbarAction);
    BAMutil.addActionToMenu(toolbarMenu, moveToolbarAction);

    BAMutil.addActionToMenu(configMenu, chooseProjectionAction);
    BAMutil.addActionToMenu(configMenu, saveCurrentProjectionAction);

    //// tools menu
    JMenu displayMenu = new JMenu("Display control");
    displayMenu.setMnemonic('D');

    BAMutil.addActionToMenu(displayMenu, showGridAction);
    BAMutil.addActionToMenu(displayMenu, showContoursAction);
    BAMutil.addActionToMenu(displayMenu, showContourLabelsAction);
    BAMutil.addActionToMenu(displayMenu, redrawAction);
    toolMenu.add(displayMenu);

    // Loop Control
    JMenu loopMenu = new JMenu("Loop control");
    loopMenu.setMnemonic('L');

    BAMutil.addActionToMenu(loopMenu, fieldLoopAction);
    BAMutil.addActionToMenu(loopMenu, levelLoopAction);
    BAMutil.addActionToMenu(loopMenu, timeLoopAction);
    BAMutil.addActionToMenu(loopMenu, runtimeLoopAction);
    toolMenu.add(loopMenu);

    // MinMax Control
    JMenu mmMenu = new JMenu("ColorScale min/max");
    mmMenu.setMnemonic('C');
    BAMutil.addActionToMenu(mmMenu, minmaxHorizAction);
    BAMutil.addActionToMenu(mmMenu, minmaxLogAction);
    BAMutil.addActionToMenu(mmMenu, minmaxHoldAction);
    toolMenu.add(mmMenu);

    // Zoom/Pan
    JMenu zoomMenu = new JMenu("Zoom/Pan");
    zoomMenu.setMnemonic('Z');
    navPanel.addActionsToMenu(zoomMenu); // items are added by NavigatedPanelToolbar
    toolMenu.add(zoomMenu);
  }

  // loop control for SuperComboBox
  private static class LoopControlAction extends AbstractAction {
    SuperComboBox scbox;

    LoopControlAction(SuperComboBox cbox) {
      this.scbox = cbox;
      BAMutil.setActionProperties(this, null, cbox.getName(), false, 0, 0);
    }

    public void actionPerformed(ActionEvent e) {
      scbox.getLoopControl().show();
    }
  }

}

