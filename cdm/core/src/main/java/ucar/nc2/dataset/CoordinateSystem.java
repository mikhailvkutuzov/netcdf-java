/*
 * Copyright (c) 1998-2018 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2.dataset;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import javax.annotation.Nullable;
import ucar.nc2.*;
import ucar.nc2.constants.AxisType;
import ucar.nc2.grid.GridAxis;
import ucar.unidata.geoloc.*;
import java.util.*;
import ucar.unidata.geoloc.projection.LatLonProjection;

/**
 * A CoordinateSystem specifies the coordinates of a Variable's values.
 *
 * Mathematically it is a vector function F from index space to Sn:
 * 
 * <pre>
 *  F(i,j,k,...) -> (S1, S2, ...Sn)
 *  where i,j,k are integers, and S is the set of reals (R) or Strings.
 * </pre>
 * 
 * The components of F are just its coordinate axes:
 * 
 * <pre>
 *  F = (A1, A2, ...An)
 *    A1(i,j,k,...) -> S1
 *    A2(i,j,k,...) -> S2
 *    An(i,j,k,...) -> Sn
 * </pre>
 *
 * Concretely, a CoordinateSystem is a set of coordinate axes, and an optional set
 * of coordinate transforms.
 * The domain rank of F is the number of dimensions it is a function of. The range rank is the number
 * of coordinate axes.
 *
 * <p>
 * An important class of CoordinateSystems are <i>georeferencing</i> Coordinate Systems, that locate a
 * Variable's values in space and time. A CoordinateSystem that has a Lat and Lon axis, or a GeoX and GeoY
 * axis and a Projection CoordinateTransform will have <i>isGeoReferencing()</i> true.
 * A CoordinateSystem that has a Height, Pressure, or GeoZ axis will have <i>hasVerticalAxis()</i> true.
 * <p>
 * Further CoordinateSystems specialization is done by "data type specific" classes such as
 * ucar.nc2.ft2.coverage.grid.GridCoordSys.
 *
 * @author caron
 * @see <a href="https://www.unidata.ucar.edu/software/netcdf-java/reference/CSObjectModel.html">
 *      Coordinate System Object Model</a>
 */
public class CoordinateSystem {
  private static org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CoordinateSystem.class);

  /**
   * TODO needed for GridCoordSys
   * 
   * @deprecated Use CoordinateSystem.builder().
   */
  @Deprecated
  protected CoordinateSystem() {}

  /**
   * Create standard name from list of axes. Sort the axes first
   * 
   * @param axes list of CoordinateAxis
   * @return CoordinateSystem name, created from axes names
   */
  public static String makeName(List<CoordinateAxis> axes) {
    List<CoordinateAxis> axesSorted = new ArrayList<>(axes);
    axesSorted.sort(new CoordinateAxis.AxisComparator());
    StringBuilder buff = new StringBuilder();
    for (int i = 0; i < axesSorted.size(); i++) {
      CoordinateAxis axis = axesSorted.get(i);
      if (i > 0)
        buff.append(" ");
      buff.append(NetcdfFiles.makeFullName(axis));
    }
    return buff.toString();
  }

  // prefer smaller ranks, in case more than one
  private CoordinateAxis lesserRank(CoordinateAxis a1, CoordinateAxis a2) {
    if (a1 == null) {
      return a2;
    }
    return (a1.getRank() <= a2.getRank()) ? a1 : a2;
  }

  /** Get the List of CoordinateAxis objects */
  public ImmutableList<CoordinateAxis> getCoordinateAxes() {
    return ImmutableList.copyOf(coordAxes);
  }

  /** Get the List of CoordinateTransform objects */
  public ImmutableList<CoordinateTransform> getCoordinateTransforms() {
    return ImmutableList.copyOf(coordTrans);
  }

  /**
   * get the name of the Coordinate System
   * 
   * @return the name of the Coordinate System
   */
  public String getName() {
    return name;
  }

  /**
   * Get the underlying NetcdfDataset
   * 
   * @return the underlying NetcdfDataset.
   */
  public NetcdfDataset getNetcdfDataset() {
    return ds;
  }

  /** get the Collection of Dimensions that constitute the domain. */
  public ImmutableCollection<Dimension> getDomain() {
    return ImmutableList.copyOf(domain);
  }

  /**
   * Get the domain rank of the coordinate system = number of dimensions it is a function of.
   * 
   * @return domain.size()
   */
  public int getRankDomain() {
    return domain.size();
  }

  /**
   * Get the range rank of the coordinate system = number of coordinate axes.
   * 
   * @return coordAxes.size()
   */
  public int getRankRange() {
    return coordAxes.size();
  }

  ///////////////////////////////////////////////////////////////////////////
  // Convenience routines for finding georeferencing axes

  /**
   * Find the CoordinateAxis that has the given AxisType.
   * If more than one, return the one with lesser rank.
   * 
   * @param type look for this axisType
   * @return CoordinateAxis of the given AxisType, else null.
   */
  @Nullable
  public CoordinateAxis findAxis(AxisType type) {
    CoordinateAxis result = null;
    for (CoordinateAxis axis : coordAxes) {
      AxisType axisType = axis.getAxisType();
      if ((axisType != null) && (axisType == type)) {
        result = lesserRank(result, axis);
      }
    }
    return result;
  }

  /** Find CoordinateAxis of one of the given types, in the order given. */
  @Nullable
  public CoordinateAxis findAxis(AxisType... axisType) {
    for (AxisType type : axisType) {
      CoordinateAxis result = findAxis(type);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  /**
   * get the CoordinateAxis with AxisType.GeoX, or null if none.
   * if more than one, choose one with smallest rank
   * 
   * @return axis of type AxisType.GeoX, or null if none
   * @deprecated use findAxis(AxisType.GeoX)
   */
  @Deprecated
  public CoordinateAxis getXaxis() {
    return xAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.GeoY, or null if none.
   * if more than one, choose one with smallest rank
   * 
   * @return axis of type AxisType.GeoY, or null if none
   * @deprecated use findAxis(AxisType.GeoY)
   */
  @Deprecated
  public CoordinateAxis getYaxis() {
    return yAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.GeoZ, or null if none.
   * if more than one, choose one with smallest rank
   * 
   * @return axis of type AxisType.GeoZ, or null if none
   * @deprecated use findAxis(AxisType.GeoZ)
   */
  @Deprecated
  public CoordinateAxis getZaxis() {
    return zAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.Time, or null if none.
   * if more than one, choose one with smallest rank
   * 
   * @return axis of type AxisType.Time, or null if none
   * @deprecated use findAxis(AxisType.Time)
   */
  @Deprecated
  public CoordinateAxis getTaxis() {
    return tAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.Lat, or null if none.
   * if more than one, choose one with smallest rank
   * 
   * @return axis of type AxisType.Lat, or null if none
   * @deprecated use findAxis(AxisType.Lat)
   */
  @Deprecated
  public CoordinateAxis getLatAxis() {
    return latAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.Lon, or null if none.
   * if more than one, choose one with smallest rank *
   * 
   * @return axis of type AxisType.Lon, or null if none
   * @deprecated use findAxis(AxisType.Lon)
   */
  @Deprecated
  public CoordinateAxis getLonAxis() {
    return lonAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.Height, or null if none.
   * if more than one, choose one with smallest rank
   * 
   * @return axis of type AxisType.Height, or null if none
   * @deprecated use findAxis(AxisType.Height)
   */
  @Deprecated
  public CoordinateAxis getHeightAxis() {
    return hAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.Pressure, or null if none.
   * if more than one, choose one with smallest rank.
   * 
   * @return axis of type AxisType.Pressure, or null if none
   * @deprecated use findAxis(AxisType.Pressure)
   */
  @Deprecated
  public CoordinateAxis getPressureAxis() {
    return pAxis;
  }


  /**
   * get the CoordinateAxis with AxisType.Ensemble, or null if none.
   * if more than one, choose one with smallest rank.
   * 
   * @return axis of type AxisType.Ensemble, or null if none
   * @deprecated use findAxis(AxisType.Ensemble)
   */
  @Deprecated
  public CoordinateAxis getEnsembleAxis() {
    return ensAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.RadialAzimuth, or null if none.
   * if more than one, choose one with smallest rank
   * 
   * @return axis of type AxisType.RadialAzimuth, or null if none
   * @deprecated use findAxis(AxisType.RadialAzimuth)
   */
  @Deprecated
  public CoordinateAxis getAzimuthAxis() {
    return aziAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.RadialDistance, or null if none.
   * if more than one, choose one with smallest rank
   * 
   * @return axis of type AxisType.RadialDistance, or null if none
   * @deprecated use findAxis(AxisType.RadialDistance)
   */
  @Deprecated
  public CoordinateAxis getRadialAxis() {
    return radialAxis;
  }

  /**
   * get the CoordinateAxis with AxisType.RadialElevation, or null if none.
   * if more than one, choose one with smallest rank
   * 
   * @return axis of type AxisType.RadialElevation, or null if none
   * @deprecated use findAxis(AxisType.RadialElevation)
   */
  @Deprecated
  public CoordinateAxis getElevationAxis() {
    return elevAxis;
  }

  /**
   * Find the first ProjectionCT from the list of CoordinateTransforms.
   * 
   * @return ProjectionCT or null if none.
   */
  public ProjectionCT getProjectionCT() {
    for (CoordinateTransform ct : coordTrans) {
      if (ct instanceof ProjectionCT)
        return (ProjectionCT) ct;
    }
    return null;
  }

  /**
   * Get the Projection for this coordinate system.
   * If isLatLon(), then returns a LatLonProjection. Otherwise, extracts the
   * projection from any ProjectionCT CoordinateTransform.
   * 
   * @return Projection or null if none.
   */
  @Nullable
  public Projection getProjection() {
    if (projection == null) {
      if (isLatLon())
        projection = new LatLonProjection();
      ProjectionCT projCT = getProjectionCT();
      if (null != projCT)
        projection = projCT.getProjection();
    }
    return projection;
  }

  ////////////////////////////////////////////////////////////////////////////
  // classification

  /**
   * true if it has X and Y CoordinateAxis, and a CoordTransform Projection
   * 
   * @return true if it has X and Y CoordinateAxis, and a CoordTransform Projection
   */
  public boolean isGeoXY() {
    if ((xAxis == null) || (yAxis == null)) {
      return false;
    }
    return null != getProjection() && !(projection instanceof LatLonProjection);
  }

  /**
   * true if it has Lat and Lon CoordinateAxis
   * 
   * @return true if it has Lat and Lon CoordinateAxis
   */
  public boolean isLatLon() {
    return (latAxis != null) && (lonAxis != null);
  }

  /**
   * true if it has radial distance and azimuth CoordinateAxis
   * 
   * @return true if it has radial distance and azimuth CoordinateAxis
   */
  public boolean isRadial() {
    return (radialAxis != null) && (aziAxis != null);
  }

  /**
   * true if isGeoXY or isLatLon
   * 
   * @return true if isGeoXY or isLatLon
   */
  public boolean isGeoReferencing() {
    return isGeoXY() || isLatLon();
  }

  /**
   * true if all axes are CoordinateAxis1D
   * 
   * @return true if all axes are CoordinateAxis1D
   * @deprecated do not use
   */
  @Deprecated
  public boolean isProductSet() {
    for (CoordinateAxis axis : coordAxes) {
      if (axis.getRank() != 1) {
        return false;
      }
    }
    return true;
  }

  /**
   * true if all axes are CoordinateAxis1D and are regular
   *
   * @return true if all axes are CoordinateAxis1D and are regular
   * @deprecated do not use
   */
  @Deprecated
  public boolean isRegular() {
    for (CoordinateAxis axis : coordAxes) {
      if (!(axis instanceof CoordinateAxis1D)) {
        return false;
      }
      if (!((CoordinateAxis1D) axis).isRegular()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Check if this Coordinate System is complete for v, ie if all its dimensions are also used by v.
   * 
   * @param v check for this variable
   * @return true if all dimensions in V (including parents) are in the domain of this coordinate system.
   */
  public boolean isComplete(Variable v) {
    return Dimensions.isSubset(Dimensions.makeDimensionsAll(v), domain);
  }

  /**
   * Check if this Coordinate System can be used for the given variable.
   * A CoordinateAxis can only be part of a Variable's CoordinateSystem if the CoordinateAxis' set of Dimensions is a
   * subset of the Variable's set of Dimensions.
   * So, a CoordinateSystem' set of Dimensions must be a subset of the Variable's set of Dimensions.
   * 
   * @param v check for this variable
   * @return true if all dimensions in the domain of this coordinate system are in V (including parents).
   */
  public boolean isCoordinateSystemFor(Variable v) {
    return Dimensions.isSubset(domain, Dimensions.makeDimensionsAll(v));
  }

  /**
   * Test if all the Dimensions in subset are in set
   * 
   * @param subset is this a subset
   * @param set of this?
   * @return true if all the Dimensions in subset are in set
   * @deprecated use Dimensions.isSubset()
   */
  @Deprecated
  public static boolean isSubset(Collection<Dimension> subset, Collection<Dimension> set) {
    for (Dimension d : subset) {
      if (!(set.contains(d))) {
        return false;
      }
    }
    return true;
  }

  /** @deprecated use Dimensions.isSubset() */
  @Deprecated
  public static boolean isSubset(Set<String> subset, Set<String> set) {
    for (String d : subset) {
      if (!(set.contains(d))) {
        return false;
      }
    }
    return true;
  }

  /** @deprecated use Dimensions.makeDomain() */
  @Deprecated
  public static Set<Dimension> makeDomain(Iterable<? extends Variable> axes) {
    Set<Dimension> domain = new HashSet<>();
    for (Variable axis : axes) {
      domain.addAll(axis.getDimensions());
    }
    return domain;
  }

  /** @deprecated use Dimensions.makeDomain().size() */
  @Deprecated
  public static int countDomain(Variable[] axes) {
    Set<Dimension> domain = new HashSet<>();
    for (Variable axis : axes) {
      domain.addAll(axis.getDimensions());
    }
    return domain.size();
  }

  /**
   * Implicit Coordinate System are constructed based on which Coordinate Variables exist for the Dimensions of the
   * Variable.
   * This is in contrast to a Coordinate System that is explicitly specified in the file.
   * 
   * @return true if this coordinate system was constructed implicitly.
   */
  public boolean isImplicit() {
    return isImplicit;
  }

  /**
   * true if has Height, Pressure, or GeoZ axis
   * 
   * @return true if has a vertical axis
   * @deprecated use findAxis(...)
   */
  @Deprecated
  public boolean hasVerticalAxis() {
    return (hAxis != null) || (pAxis != null) || (zAxis != null);
  }

  /**
   * true if has Time axis
   * 
   * @return true if has Time axis
   * @deprecated use findAxis(...)
   */
  @Deprecated
  public boolean hasTimeAxis() {
    return (tAxis != null);
  }

  /** Do we have all the axes in wantAxes, matching on full name */
  public boolean containsAxes(List<CoordinateAxis> wantAxes) {
    for (CoordinateAxis ca : wantAxes) {
      if (!containsAxis(ca.getFullName())) {
        return false;
      }
    }
    return true;
  }

  /**
   * Do we have the named axis?
   * 
   * @param axisName (full unescaped) name of axis
   * @return true if we have an axis of that name
   */
  public boolean containsAxis(String axisName) {
    for (CoordinateAxis ca : coordAxes) {
      if (ca.getFullName().equals(axisName))
        return true;
    }
    return false;
  }

  /** Do we have all the dimensions in wantDimensions? */
  public boolean containsDomain(List<Dimension> wantDimensions) {
    for (Dimension d : wantDimensions) {
      if (!domain.contains(d)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Do we have all the axes types in wantAxes?
   * 
   * @deprecated use findAxis(...)
   */
  @Deprecated
  public boolean containsAxisTypes(List<AxisType> wantAxes) {
    for (AxisType wantAxisType : wantAxes) {
      if (!containsAxisType(wantAxisType))
        return false;
    }
    return true;
  }

  /**
   * Do we have an axis of the given type?
   * 
   * @param wantAxisType want this AxisType
   * @return true if we have at least one axis of that type.
   * @deprecated use findAxis(...)
   */
  @Deprecated
  public boolean containsAxisType(AxisType wantAxisType) {
    for (CoordinateAxis ca : coordAxes) {
      if (ca.getAxisType() == wantAxisType) {
        return true;
      }
    }
    return false;
  }

  ////////////////////////////////////////////////////////////////////////////
  /**
   * Instances which have same name, axes and transforms are equal.
   */
  public boolean equals(Object oo) {
    if (this == oo)
      return true;
    if (!(oo instanceof CoordinateSystem))
      return false;
    CoordinateSystem o = (CoordinateSystem) oo;

    if (!getName().equals(o.getName()))
      return false;

    List<CoordinateAxis> oaxes = o.getCoordinateAxes();
    for (CoordinateAxis axis : getCoordinateAxes()) {
      if (!oaxes.contains(axis))
        return false;
    }

    List<CoordinateTransform> otrans = o.getCoordinateTransforms();
    for (CoordinateTransform tran : getCoordinateTransforms()) {
      if (!otrans.contains(tran))
        return false;
    }

    return true;
  }

  /** Override Object.hashCode() to implement equals. */
  public int hashCode() {
    if (hashCode == 0) {
      int result = 17;
      result = 37 * result + getName().hashCode();
      result = 37 * result + getCoordinateAxes().hashCode();
      result = 37 * result + getCoordinateTransforms().hashCode();
      hashCode = result;
    }
    return hashCode;
  }

  private volatile int hashCode;

  public String toString() {
    return name;
  }

  ////////////////////////////////////////////////////////////////////////////////////////////
  // TODO make these final and immutable in 6.
  protected NetcdfDataset ds; // needed?
  protected List<CoordinateAxis> coordAxes = new ArrayList<>();
  protected List<CoordinateTransform> coordTrans = new ArrayList<>();
  private Projection projection;

  // these are calculated
  protected String name;
  protected Set<Dimension> domain = new HashSet<>(); // set of dimension
  protected CoordinateAxis xAxis, yAxis, zAxis, tAxis, latAxis, lonAxis, hAxis, pAxis, ensAxis;
  protected CoordinateAxis aziAxis, elevAxis, radialAxis;
  protected boolean isImplicit; // where set?
  protected String dataType; // Grid, Station, etc. where set?

  protected CoordinateSystem(Builder<?> builder, NetcdfDataset ncd, List<CoordinateAxis> axes,
      List<CoordinateTransform> allTransforms) {
    this.ds = ncd;
    this.isImplicit = builder.isImplicit;

    // find referenced coordinate axes
    List<CoordinateAxis> axesList = new ArrayList<>();
    StringTokenizer stoker = new StringTokenizer(builder.coordAxesNames);
    while (stoker.hasMoreTokens()) {
      String vname = stoker.nextToken();
      for (CoordinateAxis a : axes) {
        String aname = a.getFullName();
        if (aname.equals(vname)) {
          axesList.add(a);
        }
      }
    }
    this.coordAxes = axesList;

    // calculated
    this.name = makeName(coordAxes);

    for (CoordinateAxis axis : this.coordAxes) {
      // look for AxisType
      AxisType axisType = axis.getAxisType();
      if (axisType != null) {
        if (axisType == AxisType.GeoX)
          xAxis = lesserRank(xAxis, axis);
        if (axisType == AxisType.GeoY)
          yAxis = lesserRank(yAxis, axis);
        if (axisType == AxisType.GeoZ)
          zAxis = lesserRank(zAxis, axis);
        if (axisType == AxisType.Time)
          tAxis = lesserRank(tAxis, axis);
        if (axisType == AxisType.Lat)
          latAxis = lesserRank(latAxis, axis);
        if (axisType == AxisType.Lon)
          lonAxis = lesserRank(lonAxis, axis);
        if (axisType == AxisType.Height)
          hAxis = lesserRank(hAxis, axis);
        if (axisType == AxisType.Pressure)
          pAxis = lesserRank(pAxis, axis);
        if (axisType == AxisType.Ensemble)
          ensAxis = lesserRank(ensAxis, axis);

        if (axisType == AxisType.RadialAzimuth)
          aziAxis = lesserRank(aziAxis, axis);
        if (axisType == AxisType.RadialDistance)
          radialAxis = lesserRank(radialAxis, axis);
        if (axisType == AxisType.RadialElevation)
          elevAxis = lesserRank(elevAxis, axis);
      }
      // collect dimensions
      domain.addAll(Dimensions.makeDimensionsAll(axis));
    }

    // Find the named coordinate transforms in allTransforms.
    for (String wantTransName : builder.transNames) {
      CoordinateTransform got = allTransforms.stream()
          .filter(ct -> (wantTransName.equals(ct.getName())
              || ct.attributes() != null && wantTransName.equals(ct.attributes().getName()))) // TODO what is this use
                                                                                              // case?
          .findFirst().orElse(null);
      if (got != null) {
        coordTrans.add(got);
      }

    }
  }

  public Builder<?> toBuilder() {
    return addLocalFieldsToBuilder(builder());
  }

  // Add local fields to the passed - in builder.
  protected Builder<?> addLocalFieldsToBuilder(Builder<? extends Builder<?>> b) {
    return b.setImplicit(this.isImplicit).setCoordAxesNames(this.name).addCoordinateTransforms(this.coordTrans);
  }

  /**
   * Get Builder for this class that allows subclassing.
   * 
   * @see "https://community.oracle.com/blogs/emcmanus/2010/10/24/using-builder-pattern-subclasses"
   */
  public static Builder<?> builder() {
    return new Builder2();
  }

  private static class Builder2 extends Builder<Builder2> {
    @Override
    protected Builder2 self() {
      return this;
    }
  }

  public static abstract class Builder<T extends Builder<T>> {
    public String coordAxesNames; // canonicalized list of names
    private final List<String> transNames = new ArrayList<>();
    private boolean isImplicit;
    private boolean built;

    protected abstract T self();

    // LOOK need to be canonicalized
    public T setCoordAxesNames(String names) {
      this.coordAxesNames = names;
      return self();
    }

    public T addCoordinateTransformByName(String ct) {
      transNames.add(ct);
      return self();
    }

    public T addCoordinateTransforms(Collection<CoordinateTransform> axes) {
      axes.forEach(axis -> addCoordinateTransformByName(axis.name));
      return self();
    }

    public T setImplicit(boolean isImplicit) {
      this.isImplicit = isImplicit;
      return self();
    }

    // LOOK do we really need NetcdfDataset?
    public CoordinateSystem build(NetcdfDataset ncd, List<CoordinateAxis> axes, List<CoordinateTransform> transforms) {
      if (built)
        throw new IllegalStateException("already built");
      built = true;
      return new CoordinateSystem(this, ncd, axes, transforms);
    }
  }

}
