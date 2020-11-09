package ucar.nc2.grib;

import com.google.common.collect.Iterables;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import ucar.nc2.constants.FeatureType;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.dataset.NetcdfDatasets;
import ucar.nc2.ft2.coverage.CoverageCollection;
import ucar.nc2.ft2.coverage.CoverageDatasetFactory;
import ucar.nc2.ft2.coverage.FeatureDatasetCoverage;
import ucar.nc2.internal.dataset.DatasetClassifier;
import ucar.nc2.internal.grid.GridDatasetImpl;
import ucar.unidata.util.test.TestDir;
import ucar.unidata.util.test.category.NeedsCdmUnitTest;

import java.io.IOException;
import java.util.Formatter;
import java.util.Optional;

import static com.google.common.truth.Truth.assertThat;

public class TestGribGridCompare {

  @Test
  @Category(NeedsCdmUnitTest.class)
  public void testProblem() throws IOException {
    String filename = TestDir.cdmUnitTestDir + "gribCollections/gfs_conus80/20141024/gfsConus80_dir-20141024.ncx4";
    try (FeatureDatasetCoverage covDataset = CoverageDatasetFactory.open(filename)) {
      CoverageCollection cc = covDataset.getSingleCoverageCollection();
      assertThat(cc.getCoverageType()).isEqualTo(FeatureType.FMRC);
    }

    try (NetcdfDataset ds = NetcdfDatasets.openDataset(filename)) {
      Formatter errlog = new Formatter();
      Optional<GridDatasetImpl> grido = GridDatasetImpl.create(ds, errlog);
      if (grido.isPresent()) {
        GridDatasetImpl gridDataset = grido.get();
        if (!Iterables.isEmpty(gridDataset.getGrids())) {
          DatasetClassifier dclassifier = new DatasetClassifier(ds, errlog);
          DatasetClassifier.CoordSysClassifier classifier =
              dclassifier.getCoordinateSystemsUsed().stream().findFirst().orElse(null);
          assertThat(classifier.getFeatureType()).isEqualTo(FeatureType.GRID);
        }
      }
    }
  }
}
