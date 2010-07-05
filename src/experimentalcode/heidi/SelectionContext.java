package experimentalcode.heidi;

import java.util.ArrayList;
import java.util.BitSet;

import de.lmu.ifi.dbs.elki.data.DatabaseObject;
import de.lmu.ifi.dbs.elki.database.ids.ArrayModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.visualization.visualizers.VisualizerContext;
import de.lmu.ifi.dbs.elki.visualization.visualizers.events.SelectionChangedEvent;

/**
 * Class for selections. Represents the selected Database-IDs and the selected
 * Range.
 * 
 * @author Heidi Kolb
 */
public class SelectionContext {

  /**
   * The possible states
   */
  public static final int SELECTRANGE = 0;

  public static final int SELECTDOTS = 1;

  public static final int MOVEDOT = 2;

  // TODO: define text not here, but for example in ToolVisualizers and get
  // through context
  // Position of text depending on number of associated state
  public static final String[] text1 = { "Change", "Select", "Select" };

  public static final String[] text2 = { "DB", "Range", "Items" };

  /**
   * Actual tool
   */
  public static int tool = SELECTRANGE;

  /**
   * Selected IDs
   */
  private ArrayModifiableDBIDs selectedIds = DBIDUtil.newArray();

  /**
   * Selected minimal value for each dimension
   */
  private ArrayList<Double> minValues;

  /**
   * Selected maximal value for each dimension
   */
  private ArrayList<Double> maxValues;

  /**
   * Mask to show what dimensions are set in minValues and maxValues
   */
  private BitSet mask;

  /**
   * Initializes this context
   * 
   * @param context The context
   */
  public void init(VisualizerContext<? extends DatabaseObject> context) {
    int dim = context.getDatabase().dimensionality();
    minValues = new ArrayList<Double>(dim);
    maxValues = new ArrayList<Double>(dim);
    mask = new BitSet(dim);
    for(int d = 0; d < dim; d++) {
      minValues.add(d, 0.);
      maxValues.add(d, 0.);
      mask.set(d, false);
    }
  }

  // Should be moved to VisualizerContext (as constant
  // VisualizerContext.SELECTION):
  public static final String SELECTION = "selection";

  // Should be moved to VisualizerContext (as context.getSelection()):
  public static SelectionContext getSelection(VisualizerContext<? extends DatabaseObject> context) {
    SelectionContext sel = context.getGenerics(SELECTION, SelectionContext.class);
    // Note: Alternative - but worse semantics.
    // Note: caller should handle null
    // if (sel == null) {
    // sel = new SelectionContext();
    // sel.init(context);
    // }
    return sel;
  }

  // Should be moved to VisualizerContext (as context.setSelection(selection))
  public static void setSelection(VisualizerContext<? extends DatabaseObject> context, SelectionContext selContext) {
    context.put(SELECTION, selContext);
    context.fireContextChange(new SelectionChangedEvent(context));
  }

  /**
   * Getter for the selected IDs
   * 
   * @return ArrayList<Integer>
   */
  public ArrayModifiableDBIDs getSelectedIds() {
    return selectedIds;
  }

  /**
   * Clears the selection
   * 
   * @param selectedIds
   */
  public void clearSelectedIds() {
    selectedIds.clear();
  }

  /**
   * Sets the selected DBIDs
   * 
   * @param selectedIds
   */
  public void setSelectedIds(ArrayModifiableDBIDs sel) {
    selectedIds = sel;
  }

  public ArrayList<Double> getMaxValues() {
    return minValues;
  }

  public ArrayList<Double> getMinValues() {
    return maxValues;
  }

  public void setMinValues(ArrayList<Double> minV) {
    minValues = minV;
  }

  public void setMaxValues(ArrayList<Double> maxV) {
    maxValues = maxV;
  }

  public void resetMask(VisualizerContext<? extends DatabaseObject> context) {
    mask.clear();
    int dim = context.getDatabase().dimensionality();
    for(int d = 0; d < dim; d++) {
      mask.set(d, false);
    }
  }

  public BitSet getMask() {
    return mask;
  }

  public void setMask(BitSet m) {
    mask = m;
  }

  public static int getTool() {
    return tool;
  }

  public static void setTool(int tool) {
    SelectionContext.tool = tool;
  }

  public static String[] getText1() {
    return text1;
  }

  public static String[] getText2() {
    return text2;
  }
}
