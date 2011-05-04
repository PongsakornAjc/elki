package de.lmu.ifi.dbs.elki.algorithm.clustering.correlation;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Vector;

import de.lmu.ifi.dbs.elki.algorithm.AbstractAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.DependencyDerivator;
import de.lmu.ifi.dbs.elki.algorithm.clustering.ClusteringAlgorithm;
import de.lmu.ifi.dbs.elki.algorithm.clustering.correlation.cash.CASHInterval;
import de.lmu.ifi.dbs.elki.algorithm.clustering.correlation.cash.CASHIntervalSplit;
import de.lmu.ifi.dbs.elki.data.Cluster;
import de.lmu.ifi.dbs.elki.data.Clustering;
import de.lmu.ifi.dbs.elki.data.DoubleVector;
import de.lmu.ifi.dbs.elki.data.HyperBoundingBox;
import de.lmu.ifi.dbs.elki.data.ParameterizationFunction;
import de.lmu.ifi.dbs.elki.data.model.ClusterModel;
import de.lmu.ifi.dbs.elki.data.model.CorrelationAnalysisSolution;
import de.lmu.ifi.dbs.elki.data.model.LinearEquationModel;
import de.lmu.ifi.dbs.elki.data.model.Model;
import de.lmu.ifi.dbs.elki.data.type.SimpleTypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeInformation;
import de.lmu.ifi.dbs.elki.data.type.TypeUtil;
import de.lmu.ifi.dbs.elki.data.type.VectorFieldTypeInformation;
import de.lmu.ifi.dbs.elki.database.Database;
import de.lmu.ifi.dbs.elki.database.ProxyDatabase;
import de.lmu.ifi.dbs.elki.database.QueryUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBID;
import de.lmu.ifi.dbs.elki.database.ids.DBIDUtil;
import de.lmu.ifi.dbs.elki.database.ids.DBIDs;
import de.lmu.ifi.dbs.elki.database.ids.ModifiableDBIDs;
import de.lmu.ifi.dbs.elki.database.query.distance.DistanceQuery;
import de.lmu.ifi.dbs.elki.database.relation.Relation;
import de.lmu.ifi.dbs.elki.database.relation.MaterializedRelation;
import de.lmu.ifi.dbs.elki.distance.distancefunction.WeightedDistanceFunction;
import de.lmu.ifi.dbs.elki.distance.distancevalue.DoubleDistance;
import de.lmu.ifi.dbs.elki.logging.Logging;
import de.lmu.ifi.dbs.elki.logging.progress.FiniteProgress;
import de.lmu.ifi.dbs.elki.math.linearalgebra.LinearEquationSystem;
import de.lmu.ifi.dbs.elki.math.linearalgebra.Matrix;
import de.lmu.ifi.dbs.elki.math.linearalgebra.pca.FirstNEigenPairFilter;
import de.lmu.ifi.dbs.elki.math.linearalgebra.pca.PCAFilteredRunner;
import de.lmu.ifi.dbs.elki.datasource.filter.NonNumericFeaturesException;
import de.lmu.ifi.dbs.elki.utilities.ClassGenericsUtil;
import de.lmu.ifi.dbs.elki.utilities.documentation.Description;
import de.lmu.ifi.dbs.elki.utilities.documentation.Reference;
import de.lmu.ifi.dbs.elki.utilities.documentation.Title;
import de.lmu.ifi.dbs.elki.utilities.exceptions.UnableToComplyException;
import de.lmu.ifi.dbs.elki.utilities.heap.DefaultHeap;
import de.lmu.ifi.dbs.elki.utilities.heap.DefaultHeapNode;
import de.lmu.ifi.dbs.elki.utilities.heap.HeapNode;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.AbstractParameterizer;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.ParameterException;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.constraints.GreaterConstraint;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.ListParameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameterization.Parameterization;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.DoubleParameter;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.Flag;
import de.lmu.ifi.dbs.elki.utilities.optionhandling.parameters.IntParameter;

/**
 * Provides the CASH algorithm, an subspace clustering algorithm based on the
 * hough transform.
 * <p>
 * Reference: E. Achtert, C. Böhm, J. David, P. Kröger, A. Zimek: Robust
 * clustering in arbitrarily oriented subspaces. <br>
 * In Proc. 8th SIAM Int. Conf. on Data Mining (SDM'08), Atlanta, GA, 2008
 * </p>
 * 
 * @author Elke Achtert
 * 
 * @apiviz.has CASHInterval
 * @apiviz.uses ParameterizationFunction
 * @apiviz.has LinearEquationModel
 */
// todo elke hierarchy (later)
@Title("CASH: Robust clustering in arbitrarily oriented subspaces")
@Description("Subspace clustering algorithm based on the hough transform.")
@Reference(authors = "E. Achtert, C. Böhm, J. David, P. Kröger, A. Zimek", title = "Robust clustering in arbitraily oriented subspaces", booktitle = "Proc. 8th SIAM Int. Conf. on Data Mining (SDM'08), Atlanta, GA, 2008", url = "http://www.siam.org/proceedings/datamining/2008/dm08_69_AchtertBoehmDavidKroegerZimek.pdf")
public class CASH extends AbstractAlgorithm<Clustering<Model>> implements ClusteringAlgorithm<Clustering<Model>> {
  /**
   * The logger for this class.
   */
  private static final Logging logger = Logging.getLogger(CASH.class);

  /**
   * Parameter to specify the threshold for minimum number of points in a
   * cluster, must be an integer greater than 0.
   * <p>
   * Key: {@code -cash.minpts}
   * </p>
   */
  public static final OptionID MINPTS_ID = OptionID.getOrCreateOptionID("cash.minpts", "Threshold for minimum number of points in a cluster.");

  /**
   * Parameter to specify the maximum level for splitting the hypercube, must be
   * an integer greater than 0.
   * <p>
   * Key: {@code -cash.maxlevel}
   * </p>
   */
  public static final OptionID MAXLEVEL_ID = OptionID.getOrCreateOptionID("cash.maxlevel", "The maximum level for splitting the hypercube.");

  /**
   * Parameter to specify the minimum dimensionality of the subspaces to be
   * found, must be an integer greater than 0.
   * <p>
   * Default value: {@code 1}
   * </p>
   * <p>
   * Key: {@code -cash.mindim}
   * </p>
   */
  public static final OptionID MINDIM_ID = OptionID.getOrCreateOptionID("cash.mindim", "The minimum dimensionality of the subspaces to be found.");

  /**
   * Parameter to specify the maximum jitter for distance values, must be a
   * double greater than 0.
   * <p>
   * Key: {@code -cash.jitter}
   * </p>
   */
  public static final OptionID JITTER_ID = OptionID.getOrCreateOptionID("cash.jitter", "The maximum jitter for distance values.");

  /**
   * Flag to indicate that an adjustment of the applied heuristic for choosing
   * an interval is performed after an interval is selected.
   * <p>
   * Key: {@code -cash.adjust}
   * </p>
   */
  public static final OptionID ADJUST_ID = OptionID.getOrCreateOptionID("cash.adjust", "Flag to indicate that an adjustment of the applied heuristic for choosing an interval " + "is performed after an interval is selected.");

  /**
   * Holds the value of {@link #MINPTS_ID}.
   */
  private int minPts;

  /**
   * Holds the value of {@link #MAXLEVEL_ID}.
   */
  private int maxLevel;

  /**
   * Holds the value of {@link #MINDIM_ID}.
   */
  private int minDim;

  /**
   * Holds the value of {@link #JITTER_ID}.
   */
  private double jitter;

  /**
   * Holds the value of {@link #ADJUST_ID}.
   */
  private boolean adjust;

  /**
   * Holds the dimensionality for noise.
   */
  private int noiseDim;

  /**
   * Holds a set of processed ids.
   */
  private ModifiableDBIDs processedIDs;

  /**
   * The database holding the objects.
   */
  private Relation<ParameterizationFunction> relation;

  /**
   * Constructor.
   * 
   * @param minPts MinPts parameter
   * @param maxLevel Maximum level
   * @param minDim Minimum dimensionality
   * @param jitter Jitter
   * @param adjust Adjust
   */
  public CASH(int minPts, int maxLevel, int minDim, double jitter, boolean adjust) {
    super();
    this.minPts = minPts;
    this.maxLevel = maxLevel;
    this.minDim = minDim;
    this.jitter = jitter;
    this.adjust = adjust;
  }

  /**
   * Run CASH on the relation.
   * 
   * @param database Database
   * @param relation Relation
   * @return
   * @throws IllegalStateException
   */
  public Clustering<Model> run(Database database, Relation<ParameterizationFunction> relation) throws IllegalStateException {
    this.relation = relation;
    if(logger.isVerbose()) {
      StringBuffer msg = new StringBuffer();
      msg.append("DB size: ").append(relation.size());
      msg.append("\nmin Dim: ").append(minDim);
      logger.verbose(msg.toString());
    }

    try {
      processedIDs = DBIDUtil.newHashSet(relation.size());
      noiseDim = relation.get(relation.iterDBIDs().next()).getDimensionality();

      FiniteProgress progress = logger.isVerbose() ? new FiniteProgress("CASH Clustering", relation.size(), logger) : null;
      Clustering<Model> result = doRun(relation, progress);
      if(progress != null) {
        progress.ensureCompleted(logger);
      }

      if(logger.isVerbose()) {
        StringBuffer msg = new StringBuffer();
        for(Cluster<Model> c : result.getAllClusters()) {
          if(c.getModel() instanceof LinearEquationModel) {
            LinearEquationModel s = (LinearEquationModel) c.getModel();
            msg.append("\n Cluster: Dim: " + s.getLes().subspacedim() + " size: " + c.size());
          }
          else {
            msg.append("\n Cluster: " + c.getModel().getClass().getName() + " size: " + c.size());
          }
        }
        logger.verbose(msg.toString());
      }
      return result;
    }
    catch(UnableToComplyException e) {
      throw new IllegalStateException(e);
    }
    catch(ParameterException e) {
      throw new IllegalStateException(e);
    }
    catch(NonNumericFeaturesException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Runs the CASH algorithm on the specified database, this method is
   * recursively called until only noise is left.
   * 
   * @param database the current database to run the CASH algorithm on
   * @param progress the progress object for verbose messages
   * @return a mapping of subspace dimensionalities to clusters
   * @throws UnableToComplyException if an error according to the database
   *         occurs
   * @throws ParameterException if the parameter setting is wrong
   * @throws NonNumericFeaturesException if non numeric feature vectors are used
   */
  private Clustering<Model> doRun(Relation<ParameterizationFunction> database, FiniteProgress progress) throws UnableToComplyException, ParameterException, NonNumericFeaturesException {
    Clustering<Model> res = new Clustering<Model>("CASH clustering", "cash-clustering");

    int dim = database.get(database.iterDBIDs().next()).getDimensionality();

    // init heap
    DefaultHeap<Integer, CASHInterval> heap = new DefaultHeap<Integer, CASHInterval>(false);
    ModifiableDBIDs noiseIDs = getDatabaseIDs(database);
    initHeap(heap, database, dim, noiseIDs);

    if(logger.isDebugging()) {
      StringBuffer msg = new StringBuffer();
      msg.append("XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
      msg.append("\nXXXX dim ").append(dim);
      msg.append("\nXXXX database.size ").append(database.size());
      msg.append("\nXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX");
      logger.debugFine(msg.toString());
    }
    else if(logger.isVerbose()) {
      StringBuffer msg = new StringBuffer();
      msg.append("XXXX dim ").append(dim).append(" database.size ").append(database.size());
      logger.verbose(msg.toString());
    }

    // get the ''best'' d-dimensional intervals at max level
    while(!heap.isEmpty()) {
      CASHInterval interval = determineNextIntervalAtMaxLevel(heap);
      if(logger.isDebugging()) {
        logger.debugFine("next interval in dim " + dim + ": " + interval);
      }
      else if(logger.isVerbose()) {
        logger.verbose("next interval in dim " + dim + ": " + interval);
      }

      // only noise left
      if(interval == null) {
        break;
      }

      // do a dim-1 dimensional run
      ModifiableDBIDs clusterIDs = DBIDUtil.newHashSet();
      if(dim > minDim + 1) {
        ModifiableDBIDs ids;
        Matrix basis_dim_minus_1;
        if(adjust) {
          ids = DBIDUtil.newHashSet();
          basis_dim_minus_1 = runDerivator(database, dim, interval, ids);
        }
        else {
          ids = interval.getIDs();
          basis_dim_minus_1 = determineBasis(interval.centroid());
        }

        if(ids.size() != 0) {
          MaterializedRelation<ParameterizationFunction> db = buildDB(dim, basis_dim_minus_1, ids, database);
          // add result of dim-1 to this result
          Clustering<Model> res_dim_minus_1 = doRun(db, progress);
          for(Cluster<Model> cluster : res_dim_minus_1.getAllClusters()) {
            res.addCluster(cluster);
            noiseIDs.removeAll(cluster.getIDs().asCollection());
            clusterIDs.addAll(cluster.getIDs().asCollection());
            processedIDs.addAll(cluster.getIDs().asCollection());
          }
        }
      }
      // dim == minDim
      else {
        LinearEquationSystem les = runDerivator(database, dim - 1, interval.getIDs());
        Cluster<Model> c = new Cluster<Model>(interval.getIDs(), new LinearEquationModel(les));
        res.addCluster(c);
        noiseIDs.removeDBIDs(interval.getIDs());
        clusterIDs.addDBIDs(interval.getIDs());
        processedIDs.addAll(interval.getIDs());
      }

      // reorganize heap
      Vector<HeapNode<Integer, CASHInterval>> heapVector = heap.copy();
      heap.clear();
      for(HeapNode<Integer, CASHInterval> heapNode : heapVector) {
        CASHInterval currentInterval = heapNode.getValue();
        currentInterval.removeIDs(clusterIDs);
        if(currentInterval.getIDs().size() >= minPts) {
          heap.addNode(new DefaultHeapNode<Integer, CASHInterval>(currentInterval.priority(), currentInterval));
        }
      }

      if(progress != null) {
        progress.setProcessed(processedIDs.size(), logger);
      }
    }

    // put noise to clusters
    if(!noiseIDs.isEmpty()) {
      if(dim == noiseDim) {
        Cluster<Model> c = new Cluster<Model>(noiseIDs, true, ClusterModel.CLUSTER);
        res.addCluster(c);
        processedIDs.addAll(noiseIDs);
      }
      else if(noiseIDs.size() >= minPts) {
        // TODO: use different class/model for noise, even when LES was
        // computed?
        LinearEquationSystem les = runDerivator(relation, dim - 1, noiseIDs);
        Cluster<Model> c = new Cluster<Model>(noiseIDs, new LinearEquationModel(les));
        res.addCluster(c);
        processedIDs.addAll(noiseIDs);
      }
    }

    if(logger.isDebugging()) {
      StringBuffer msg = new StringBuffer();
      msg.append("noise fuer dim ").append(dim).append(": ").append(noiseIDs.size());

      for(Cluster<Model> c : res.getAllClusters()) {
        if(c.getModel() instanceof LinearEquationModel) {
          LinearEquationModel s = (LinearEquationModel) c.getModel();
          msg.append("\n Cluster: Dim: " + s.getLes().subspacedim() + " size: " + c.size());
        }
        else {
          msg.append("\n Cluster: " + c.getModel().getClass().getName() + " size: " + c.size());
        }
      }
      logger.debugFine(msg.toString());
    }

    if(progress != null) {
      progress.setProcessed(processedIDs.size(), logger);
    }
    return res;
  }

  /**
   * Initializes the heap with the root intervals.
   * 
   * @param heap the heap to be initialized
   * @param database the database storing the parameterization functions
   * @param dim the dimensionality of the database
   * @param ids the ids of the database
   */
  private void initHeap(DefaultHeap<Integer, CASHInterval> heap, Relation<ParameterizationFunction> database, int dim, DBIDs ids) {
    CASHIntervalSplit split = new CASHIntervalSplit(database, minPts);

    // determine minimum and maximum function value of all functions
    double[] minMax = determineMinMaxDistance(database, dim);

    double d_min = minMax[0];
    double d_max = minMax[1];
    double dIntervalLength = d_max - d_min;
    int numDIntervals = (int) Math.ceil(dIntervalLength / jitter);
    double dIntervalSize = dIntervalLength / numDIntervals;
    double[] d_mins = new double[numDIntervals];
    double[] d_maxs = new double[numDIntervals];

    if(logger.isDebugging()) {
      StringBuffer msg = new StringBuffer();
      msg.append("d_min ").append(d_min);
      msg.append("\nd_max ").append(d_max);
      msg.append("\nnumDIntervals ").append(numDIntervals);
      msg.append("\ndIntervalSize ").append(dIntervalSize);
      logger.debugFine(msg.toString());
    }
    else if(logger.isVerbose()) {
      StringBuffer msg = new StringBuffer();
      msg.append("d_min ").append(d_min);
      msg.append("\nd_max ").append(d_max);
      msg.append("\nnumDIntervals ").append(numDIntervals);
      msg.append("\ndIntervalSize ").append(dIntervalSize);
      logger.verbose(msg.toString());
    }

    // alpha intervals
    double[] alphaMin = new double[dim - 1];
    double[] alphaMax = new double[dim - 1];
    Arrays.fill(alphaMax, Math.PI);

    for(int i = 0; i < numDIntervals; i++) {
      if(i == 0) {
        d_mins[i] = d_min;
      }
      else {
        d_mins[i] = d_maxs[i - 1];
      }

      if(i < numDIntervals - 1) {
        d_maxs[i] = d_mins[i] + dIntervalSize;
      }
      else {
        d_maxs[i] = d_max - d_mins[i];
      }

      HyperBoundingBox alphaInterval = new HyperBoundingBox(alphaMin, alphaMax);
      ModifiableDBIDs intervalIDs = split.determineIDs(ids, alphaInterval, d_mins[i], d_maxs[i]);
      if(intervalIDs != null && intervalIDs.size() >= minPts) {
        CASHInterval rootInterval = new CASHInterval(alphaMin, alphaMax, split, intervalIDs, 0, 0, d_mins[i], d_maxs[i]);
        heap.addNode(new DefaultHeapNode<Integer, CASHInterval>(rootInterval.priority(), rootInterval));
      }
    }

    if(logger.isDebuggingFiner()) {
      StringBuffer msg = new StringBuffer();
      msg.append("heap.size ").append(heap.size());
      logger.debugFiner(msg.toString());
    }
  }

  /**
   * Builds a dim-1 dimensional database where the objects are projected into
   * the specified subspace.
   * 
   * @param dim the dimensionality of the database
   * @param basis the basis defining the subspace
   * @param ids the ids for the new database
   * @param database the database storing the parameterization functions
   * @return a dim-1 dimensional database where the objects are projected into
   *         the specified subspace
   * @throws UnableToComplyException if an error according to the database
   *         occurs
   */
  private MaterializedRelation<ParameterizationFunction> buildDB(int dim, Matrix basis, DBIDs ids, Relation<ParameterizationFunction> database) throws UnableToComplyException {
    ProxyDatabase proxy = new ProxyDatabase(ids);
    VectorFieldTypeInformation<ParameterizationFunction> type = VectorFieldTypeInformation.get(ParameterizationFunction.class, basis.getColumnDimensionality());
    MaterializedRelation<ParameterizationFunction> prep = new MaterializedRelation<ParameterizationFunction>(proxy, type, ids);
    proxy.addRelation(prep);
    
    // Project
    for(DBID id : ids) {
      ParameterizationFunction f = project(basis, database.get(id));
      prep.set(id, f);
    }

    if(logger.isDebugging()) {
      logger.debugFine("db fuer dim " + (dim - 1) + ": " + ids.size());
    }

    return prep;
  }

  /**
   * Projects the specified parameterization function into the subspace
   * described by the given basis.
   * 
   * @param basis the basis defining he subspace
   * @param f the parameterization function to be projected
   * @return the projected parameterization function
   */
  private ParameterizationFunction project(Matrix basis, ParameterizationFunction f) {
    // Matrix m = new Matrix(new
    // double[][]{f.getPointCoordinates()}).times(basis);
    Matrix m = f.getRowVector().times(basis);
    ParameterizationFunction f_t = new ParameterizationFunction(m.getColumnPackedCopy());
    return f_t;
  }

  /**
   * Determines a basis defining a subspace described by the specified alpha
   * values.
   * 
   * @param alpha the alpha values
   * @return a basis defining a subspace described by the specified alpha values
   */
  private Matrix determineBasis(double[] alpha) {
    double[] nn = new double[alpha.length + 1];
    for(int i = 0; i < nn.length; i++) {
      double alpha_i = i == alpha.length ? 0 : alpha[i];
      nn[i] = sinusProduct(0, i, alpha) * StrictMath.cos(alpha_i);
    }
    Matrix n = new Matrix(nn, alpha.length + 1);
    return n.completeToOrthonormalBasis();
  }

  /**
   * Computes the product of all sinus values of the specified angles from start
   * to end index.
   * 
   * @param start the index to start
   * @param end the index to end
   * @param alpha the array of angles
   * @return the product of all sinus values of the specified angles from start
   *         to end index
   */
  private double sinusProduct(int start, int end, double[] alpha) {
    double result = 1;
    for(int j = start; j < end; j++) {
      result *= StrictMath.sin(alpha[j]);
    }
    return result;
  }

  /**
   * Determines the next ''best'' interval at maximum level, i.e. the next
   * interval containing the most unprocessed objects.
   * 
   * @param heap the heap storing the intervals
   * @return the next ''best'' interval at maximum level
   */
  private CASHInterval determineNextIntervalAtMaxLevel(DefaultHeap<Integer, CASHInterval> heap) {
    CASHInterval next = doDetermineNextIntervalAtMaxLevel(heap);
    // noise path was chosen
    while(next == null) {
      if(heap.isEmpty()) {
        return null;
      }
      next = doDetermineNextIntervalAtMaxLevel(heap);
    }

    return next;
  }

  /**
   * Recursive helper method to determine the next ''best'' interval at maximum
   * level, i.e. the next interval containing the most unprocessed objects
   * 
   * @param heap the heap storing the intervals
   * @return the next ''best'' interval at maximum level
   */
  private CASHInterval doDetermineNextIntervalAtMaxLevel(DefaultHeap<Integer, CASHInterval> heap) {
    CASHInterval interval = heap.getMinNode().getValue();
    int dim = interval.getDimensionality();
    while(true) {
      // max level is reached
      if(interval.getLevel() >= maxLevel && interval.getMaxSplitDimension() == dim) {
        return interval;
      }

      if(heap.size() % 10000 == 0 && logger.isVerbose()) {
        logger.verbose("heap size " + heap.size());
      }

      if(heap.size() >= 40000) {
        logger.warning("Heap size > 40.000!!!");
        heap.clear();
        return null;
      }

      if(logger.isDebuggingFiner()) {
        logger.debugFiner("split " + interval.toString() + " " + interval.getLevel() + "-" + interval.getMaxSplitDimension());
      }
      interval.split();

      // noise
      if(!interval.hasChildren()) {
        return null;
      }

      CASHInterval bestInterval;
      if(interval.getLeftChild() != null && interval.getRightChild() != null) {
        int comp = interval.getLeftChild().compareTo(interval.getRightChild());
        if(comp < 0) {
          bestInterval = interval.getRightChild();
          heap.addNode(new DefaultHeapNode<Integer, CASHInterval>(interval.getLeftChild().priority(), interval.getLeftChild()));
        }
        else {
          bestInterval = interval.getLeftChild();
          heap.addNode(new DefaultHeapNode<Integer, CASHInterval>(interval.getRightChild().priority(), interval.getRightChild()));
        }
      }
      else if(interval.getLeftChild() == null) {
        bestInterval = interval.getRightChild();
      }
      else {
        bestInterval = interval.getLeftChild();
      }

      interval = bestInterval;
    }
  }

  /**
   * Returns the set of ids belonging to the specified database.
   * 
   * @param database the database containing the parameterization functions.
   * @return the set of ids belonging to the specified database
   */
  private ModifiableDBIDs getDatabaseIDs(Relation<ParameterizationFunction> database) {
    return DBIDUtil.newHashSet(database.getDBIDs());
  }

  /**
   * Determines the minimum and maximum function value of all parameterization
   * functions stored in the specified database.
   * 
   * @param database the database containing the parameterization functions.
   * @param dimensionality the dimensionality of the database
   * @return an array containing the minimum and maximum function value of all
   *         parameterization functions stored in the specified database
   */
  private double[] determineMinMaxDistance(Relation<ParameterizationFunction> database, int dimensionality) {
    double[] min = new double[dimensionality - 1];
    double[] max = new double[dimensionality - 1];
    Arrays.fill(max, Math.PI);
    HyperBoundingBox box = new HyperBoundingBox(min, max);

    double d_min = Double.POSITIVE_INFINITY;
    double d_max = Double.NEGATIVE_INFINITY;
    for(Iterator<DBID> it = database.iterDBIDs(); it.hasNext();) {
      DBID id = it.next();
      ParameterizationFunction f = database.get(id);
      HyperBoundingBox minMax = f.determineAlphaMinMax(box);
      double f_min = f.function(minMax.getMin());
      double f_max = f.function(minMax.getMax());

      d_min = Math.min(d_min, f_min);
      d_max = Math.max(d_max, f_max);
    }
    return new double[] { d_min, d_max };
  }

  /**
   * Runs the derivator on the specified interval and assigns all points having
   * a distance less then the standard deviation of the derivator model to the
   * model to this model.
   * 
   * @param database the database containing the parameterization functions
   * @param interval the interval to build the model
   * @param dim the dimensionality of the database
   * @param ids an empty set to assign the ids
   * @return a basis of the found subspace
   * @throws UnableToComplyException if an error according to the database
   *         occurs
   * @throws ParameterException if the parameter setting is wrong
   */
  private Matrix runDerivator(Relation<ParameterizationFunction> database, int dim, CASHInterval interval, ModifiableDBIDs ids) throws UnableToComplyException, ParameterException {
    // build database for derivator
    Database derivatorDB = buildDerivatorDB(database, interval);

    // set the parameters
    ListParameterization parameters = new ListParameterization();
    parameters.addParameter(PCAFilteredRunner.PCA_EIGENPAIR_FILTER, FirstNEigenPairFilter.class.getName());
    parameters.addParameter(FirstNEigenPairFilter.EIGENPAIR_FILTER_N, Integer.toString(dim - 1));
    DependencyDerivator<DoubleVector, DoubleDistance> derivator = null;
    Class<DependencyDerivator<DoubleVector, DoubleDistance>> cls = ClassGenericsUtil.uglyCastIntoSubclass(DependencyDerivator.class);
    derivator = parameters.tryInstantiate(cls);

    CorrelationAnalysisSolution<DoubleVector> model = derivator.run(derivatorDB);

    Matrix weightMatrix = model.getSimilarityMatrix();
    DoubleVector centroid = new DoubleVector(model.getCentroid());
    DistanceQuery<DoubleVector, DoubleDistance> df = QueryUtil.getDistanceQuery(derivatorDB, new WeightedDistanceFunction(weightMatrix));
    DoubleDistance eps = df.getDistanceFactory().parseString("0.25");

    ids.addDBIDs(interval.getIDs());
    // Search for nearby vectors in original database
    for(Iterator<DBID> it = database.iterDBIDs(); it.hasNext();) {
      DBID id = it.next();
      DoubleVector v = new DoubleVector(database.get(id).getRowVector().getRowPackedCopy());
      DoubleDistance d = df.distance(v, centroid);
      if(d.compareTo(eps) < 0) {
        ids.add(id);
      }
    }

    Matrix basis = model.getStrongEigenvectors();
    return basis.getMatrix(0, basis.getRowDimensionality() - 1, 0, dim - 2);
  }

  /**
   * Builds a database for the derivator consisting of the ids in the specified
   * interval.
   * 
   * @param database the database storing the parameterization functions
   * @param interval the interval to build the database from
   * @return a database for the derivator consisting of the ids in the specified
   *         interval
   * @throws UnableToComplyException if an error according to the database
   *         occurs
   */
  private Database buildDerivatorDB(Relation<ParameterizationFunction> database, CASHInterval interval) throws UnableToComplyException {
    DBIDs ids = interval.getIDs();
    ProxyDatabase proxy = new ProxyDatabase(ids);
    int dim = database.get(ids.iterator().next()).getRowVector().getRowPackedCopy().length;
    SimpleTypeInformation<DoubleVector> type = new VectorFieldTypeInformation<DoubleVector>(DoubleVector.class, dim, new DoubleVector(new double[dim]));
    MaterializedRelation<DoubleVector> prep = new MaterializedRelation<DoubleVector>(proxy, type, ids);
    proxy.addRelation(prep);

    // Project
    for(DBID id : ids) {
      DoubleVector v = new DoubleVector(database.get(id).getRowVector().getRowPackedCopy());
      prep.set(id, v);
    }

    if(logger.isDebugging()) {
      logger.debugFine("db fuer derivator : " + prep.size());
    }

    return proxy;
  }

  /**
   * Runs the derivator on the specified interval and assigns all points having
   * a distance less then the standard deviation of the derivator model to the
   * model to this model.
   * 
   * @param database the database containing the parameterization functions
   * @param ids the ids to build the model
   * @param dimensionality the dimensionality of the subspace
   * @return a basis of the found subspace
   */
  private LinearEquationSystem runDerivator(Relation<ParameterizationFunction> database, int dimensionality, DBIDs ids) {
    try {
      // build database for derivator
      Database derivatorDB = buildDerivatorDB(database, ids);

      ListParameterization parameters = new ListParameterization();
      parameters.addParameter(PCAFilteredRunner.PCA_EIGENPAIR_FILTER, FirstNEigenPairFilter.class.getName());
      parameters.addParameter(FirstNEigenPairFilter.EIGENPAIR_FILTER_N, Integer.toString(dimensionality));
      DependencyDerivator<DoubleVector, DoubleDistance> derivator = null;
      Class<DependencyDerivator<DoubleVector, DoubleDistance>> cls = ClassGenericsUtil.uglyCastIntoSubclass(DependencyDerivator.class);
      derivator = parameters.tryInstantiate(cls);

      CorrelationAnalysisSolution<DoubleVector> model = derivator.run(derivatorDB);
      LinearEquationSystem les = model.getNormalizedLinearEquationSystem(null);
      return les;
    }
    catch(UnableToComplyException e) {
      throw new IllegalStateException("Initialization of the database for the derivator failed: " + e);
    }
    catch(NonNumericFeaturesException e) {
      throw new IllegalStateException("Error during normalization" + e);
    }
  }

  /**
   * Builds a database for the derivator consisting of the ids in the specified
   * interval.
   * 
   * @param database the database storing the parameterization functions
   * @param ids the ids to build the database from
   * @return a database for the derivator consisting of the ids in the specified
   *         interval
   * @throws UnableToComplyException if initialization of the database is not
   *         possible
   */
  private Database buildDerivatorDB(Relation<ParameterizationFunction> database, DBIDs ids) throws UnableToComplyException {
    ProxyDatabase proxy = new ProxyDatabase(ids);
    int dim = database.get(ids.iterator().next()).getRowVector().getRowPackedCopy().length;
    SimpleTypeInformation<DoubleVector> type = new VectorFieldTypeInformation<DoubleVector>(DoubleVector.class, dim, new DoubleVector(new double[dim]));
    MaterializedRelation<DoubleVector> prep = new MaterializedRelation<DoubleVector>(proxy, type, ids);
    proxy.addRelation(prep);

    // Project
    for(DBID id : ids) {
      DoubleVector v = new DoubleVector(database.get(id).getRowVector().getRowPackedCopy());
      prep.set(id, v);
    }

    return proxy;
  }

  @Override
  public TypeInformation[] getInputTypeRestriction() {
    return TypeUtil.array(VectorFieldTypeInformation.get(ParameterizationFunction.class));
  }

  @Override
  protected Logging getLogger() {
    return logger;
  }

  /**
   * Parameterization class.
   * 
   * @author Erich Schubert
   * 
   * @apiviz.exclude
   */
  public static class Parameterizer extends AbstractParameterizer {
    protected int minpts;

    protected int maxlevel;

    protected int mindim;

    protected double jitter;

    protected boolean adjust;

    @Override
    protected void makeOptions(Parameterization config) {
      super.makeOptions(config);
      IntParameter minptsP = new IntParameter(MINPTS_ID, new GreaterConstraint(0));
      if(config.grab(minptsP)) {
        minpts = minptsP.getValue();
      }
      IntParameter maxlevelP = new IntParameter(MAXLEVEL_ID, new GreaterConstraint(0));
      if(config.grab(maxlevelP)) {
        maxlevel = maxlevelP.getValue();
      }
      IntParameter mindimP = new IntParameter(MINDIM_ID, new GreaterConstraint(0), 1);
      if(config.grab(mindimP)) {
        mindim = mindimP.getValue();
      }
      DoubleParameter jitterP = new DoubleParameter(JITTER_ID, new GreaterConstraint(0));
      if(config.grab(jitterP)) {
        jitter = jitterP.getValue();
      }
      Flag adjustF = new Flag(ADJUST_ID);
      if(config.grab(adjustF)) {
        adjust = adjustF.getValue();
      }
    }

    @Override
    protected CASH makeInstance() {
      return new CASH(minpts, maxlevel, mindim, jitter, adjust);
    }
  }
}