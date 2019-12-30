/*
 * Copyright (c) 1998-2019 University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */

package ucar.nc2;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import software.amazon.awssdk.regions.Region;
import ucar.ma2.Array;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Section;
import ucar.unidata.util.test.category.NeedsExternalResource;

@Category(NeedsExternalResource.class)
public class TestS3Read {

  @Test
  public void testFullS3Read() throws IOException {
    String region = Region.US_EAST_1.toString();
    String bucket = "noaa-goes16";
    String key =
        "ABI-L1b-RadC/2019/363/21/OR_ABI-L1b-RadC-M6C16_G16_s20193632101189_e20193632103574_c20193632104070.nc";
    String s3uri = "s3://" + bucket + "/" + key;

    System.setProperty("aws.region", region);
    try (NetcdfFile ncfile = NetcdfFiles.open(s3uri)) {

      Dimension x = ncfile.findDimension("x");
      Dimension y = ncfile.findDimension("y");
      Assert.assertNotNull(x);
      Assert.assertNotNull(y);

      Variable radiance = ncfile.findVariable("Rad");
      Assert.assertNotNull(radiance);

      // read full array
      Array array = radiance.read();
      Assert.assertEquals(2, array.getRank());

      // check shape of array is the same as the shape of the variable
      int[] variableShape = radiance.getShape();
      int[] arrayShape = array.getShape();
      for (int dimNumber = 0; dimNumber < array.getRank(); dimNumber++) {
        Assert.assertEquals(variableShape[dimNumber], arrayShape[dimNumber]);
      }
    } finally {
      System.clearProperty("aws.region");
    }
  }

  @Test
  public void testPartialS3Read() throws IOException, InvalidRangeException {
    String region = Region.US_EAST_1.toString();
    String bucket = "noaa-goes16";
    String key =
        "ABI-L1b-RadC/2019/363/21/OR_ABI-L1b-RadC-M6C16_G16_s20193632101189_e20193632103574_c20193632104070.nc";
    String s3uri = "s3://" + bucket + "/" + key;

    System.setProperty("aws.region", region);
    try (NetcdfFile ncfile = NetcdfFiles.open(s3uri)) {

      Variable radiance = ncfile.findVariable("Rad");
      Assert.assertNotNull(radiance);

      // read part of the array
      Section section = new Section("(100:200:2,10:20:1)");
      Array array = radiance.read(section);
      Assert.assertEquals(2, array.getRank());


      // check shape of array is the same as the shape of the section
      int[] sectionShape = section.getShape();
      int[] arrayShape = array.getShape();
      for (int dimNumber = 0; dimNumber < array.getRank(); dimNumber++) {
        Assert.assertEquals(sectionShape[dimNumber], arrayShape[dimNumber]);
      }
    } finally {
      System.clearProperty("aws.region");
    }
  }
}
