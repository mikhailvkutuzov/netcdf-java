package ucar.nc2.grid;

import com.google.common.collect.ImmutableList;
import ucar.nc2.constants.FeatureType;

import java.io.Closeable;
import java.util.Formatter;
import java.util.Optional;

public interface GridDataset extends Closeable {

  String getName();

  String getLocation();

  FeatureType getFeatureType();

  ImmutableList<GridCoordinateSystem> getCoordSystems();

  ImmutableList<GridAxis> getCoordAxes();

  ImmutableList<Grid> getGrids();

  Optional<Grid> findGrid(String name);

  void toString(Formatter f);

}
