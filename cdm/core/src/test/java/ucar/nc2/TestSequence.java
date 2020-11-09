/*
 * Copyright (c) 1998-2020 John Caron and University Corporation for Atmospheric Research/Unidata
 * See LICENSE for license information.
 */
package ucar.nc2;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;
import static ucar.nc2.TestUtils.makeDummyGroup;

import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Formatter;
import org.junit.Test;
import ucar.array.StructureDataArray;
import ucar.array.StructureDataStorageBB;
import ucar.array.StructureMembers;
import ucar.ma2.Array;
import ucar.ma2.ArrayStructure;
import ucar.ma2.ArrayStructureW;
import ucar.ma2.DataType;
import ucar.ma2.InvalidRangeException;
import ucar.ma2.Range;
import ucar.ma2.Section;
import ucar.ma2.StructureData;
import ucar.ma2.StructureDataIterator;
import ucar.ma2.StructureDataScalar;
import ucar.nc2.internal.util.CompareNetcdf2;

/** Test {@link ucar.nc2.Sequence} */
public class TestSequence {

  @Test
  public void testSequence() throws IOException, InvalidRangeException {
    Sequence.Builder<?> structb = Sequence.builder().setName("seq").addMemberVariable("one", DataType.BYTE, "")
        .addMemberVariable("two", DataType.STRING, "").addMemberVariable("tres", DataType.FLOAT, "");

    StructureData sdata = makeStructureData(1);
    ArrayStructureW cacheData = new ArrayStructureW(sdata.getStructureMembers(), new int[] {2, 2});
    for (int i = 0; i < 4; i++) {
      cacheData.setStructureData(makeStructureData(i + 1), i);
    }
    structb.setSourceData(cacheData);
    Structure struct = structb.build(makeDummyGroup());

    Array data = struct.read();
    assertThat(data).isNotNull();
    assertThat(data).isInstanceOf(ArrayStructure.class);
    ArrayStructure as = (ArrayStructure) data;
    try (StructureDataIterator iter = as.getStructureDataIterator()) {
      int count = 0;
      while (iter.hasNext()) {
        StructureData sd = iter.next();
        assertThat(compare(sd, makeStructureData(count + 1))).isTrue();
        count++;
      }
      assertThat(count).isEqualTo(4);
    }

    try (StructureDataIterator iter = struct.getStructureIterator()) {
      int count = 0;
      while (iter.hasNext()) {
        StructureData sd = iter.next();
        assertThat(compare(sd, makeStructureData(count + 1))).isTrue();
        count++;
      }
      assertThat(count).isEqualTo(4);
    }

    Array data2 = struct.read(new Section());

    Formatter f = new Formatter();
    CompareNetcdf2 compare = new CompareNetcdf2(f);
    boolean ok = compare.compareData("testSequence", data, data2, false);
    if (!ok) {
      System.out.printf("%s%n", f);
    }
    assertThat(ok).isTrue();
  }

  @Test
  public void testSequenceArray() throws IOException, InvalidRangeException {
    Sequence.Builder<?> structb = Sequence.builder().setName("seq").addMemberVariable("one", DataType.BYTE, "")
        .addMemberVariable("two", DataType.STRING, "").addMemberVariable("tres", DataType.FLOAT, "");

    structb.setSourceData(makeStructureDataArray());
    Structure struct = structb.build(makeDummyGroup());

    ucar.array.Array<?> data = struct.readArray();
    assertThat(data).isNotNull();
    assertThat(data).isInstanceOf(ucar.array.StructureDataArray.class);
    ucar.array.StructureDataArray as = (ucar.array.StructureDataArray) data;
    int count = 0;
    for (ucar.array.StructureData sd : as) {
      count++;
    }
    assertThat(count).isEqualTo(2);
  }

  private StructureDataArray makeStructureDataArray() {
    StructureMembers.Builder builder = StructureMembers.builder();
    builder.setName("name");
    builder.addMember("mbyte", "mdesc1", "munits1", DataType.BYTE, new int[] {11, 11});
    builder.addMember("mfloat", "mdesc2", "munits1", DataType.FLOAT, new int[] {});
    builder.setStandardOffsets(false);
    StructureMembers members = builder.build();

    int nrows = 2;
    ByteBuffer bbuffer = ByteBuffer.allocate(nrows * members.getStorageSizeBytes());
    StructureDataStorageBB storage = new StructureDataStorageBB(members, bbuffer, nrows);
    for (int row = 0; row < nrows; row++) {
      for (StructureMembers.Member m : members) {
        if (m.getName().equals("mbyte")) {
          for (int i = 0; i < m.length(); i++) {
            bbuffer.put((byte) i);
          }
        } else if (m.getName().equals("mbyte")) {
          bbuffer.putFloat(99.5f);
        }
      }
    }
    return new StructureDataArray(members, new int[] {nrows}, storage);
  }

  @Test
  public void testUnsupportedMethods() {
    Sequence.Builder<?> structb = Sequence.builder().setName("seq").addMemberVariable("one", DataType.BYTE, "")
        .addMemberVariable("two", DataType.STRING, "").addMemberVariable("tres", DataType.FLOAT, "");

    StructureData sdata = makeStructureData(1);
    ArrayStructureW cacheData = new ArrayStructureW(sdata.getStructureMembers(), new int[] {2, 2});
    for (int i = 0; i < 4; i++) {
      cacheData.setStructureData(makeStructureData(i + 1), i);
    }
    structb.setSourceData(cacheData);
    Structure struct = structb.build(makeDummyGroup());

    try {
      struct.read(new int[] {0}, new int[] {0});
      fail();
    } catch (Exception e) {
      // expected
    }

    try {
      struct.read("0:0");
      fail();
    } catch (Exception e) {
      // expected
    }

    try {
      struct.read(ImmutableList.of(new Range(0, 0)));
      fail();
    } catch (Exception e) {
      // expected
    }

    try {
      struct.readStructure(0);
      fail();
    } catch (Exception e) {
      // expected
    }

    try {
      struct.readStructure(0, 1);
      fail();
    } catch (Exception e) {
      // expected
    }

    try {
      struct.slice(0, 1);
      fail();
    } catch (Exception e) {
      // expected
    }

    try {
      struct.section(new Section());
      fail();
    } catch (Exception e) {
      // expected
    }

  }

  @Test
  public void testToBuilder() throws IOException {
    Sequence.Builder<?> structb = Sequence.builder().setName("seq").addMemberVariable("one", DataType.BYTE, "")
        .addMemberVariable("two", DataType.STRING, "").addMemberVariable("tres", DataType.FLOAT, "");

    StructureData sdata = makeStructureData(1);
    ArrayStructureW cacheData = new ArrayStructureW(sdata.getStructureMembers(), new int[] {2, 2});
    for (int i = 0; i < 4; i++) {
      cacheData.setStructureData(makeStructureData(i + 1), i);
    }
    structb.setSourceData(cacheData);
    Structure struct1 = structb.build(makeDummyGroup());
    Structure struct2 = struct1.toBuilder().setName("struct2").build(makeDummyGroup());

    Array data = struct2.read();
    assertThat(data).isNotNull();
    assertThat(data).isInstanceOf(ArrayStructure.class);
    ArrayStructure as = (ArrayStructure) data;
    try (StructureDataIterator iter = as.getStructureDataIterator()) {
      int count = 0;
      while (iter.hasNext()) {
        StructureData sd = iter.next();
        assertThat(compare(sd, makeStructureData(count + 1))).isTrue();
        count++;
      }
      assertThat(count).isEqualTo(4);
    }
  }

  @Test
  public void testNoData() {
    Sequence.Builder<?> structb = Sequence.builder().setName("seq").addMemberVariable("one", DataType.BYTE, "")
        .addMemberVariable("two", DataType.STRING, "").addMemberVariable("tres", DataType.FLOAT, "");
    Structure struct = structb.build(makeDummyGroup());

    try {
      struct.getStructureIterator();
      fail();
    } catch (Exception e) {
      // expected
    }
  }

  private StructureData makeStructureData(int elem) {
    StructureDataScalar sdata = new StructureDataScalar("struct");
    sdata.addMember("one", "desc1", "units1", DataType.BYTE, (byte) elem);
    sdata.addMemberString("two", "desc2", "units2", "two", 4);
    sdata.addMember("tres", "desc3", "units4", DataType.FLOAT, elem * 3.0f);
    return sdata;
  }

  private boolean compare(StructureData sdata1, StructureData sdata2) {
    Formatter f = new Formatter();
    CompareNetcdf2 compare = new CompareNetcdf2(f);
    boolean ok = compare.compareStructureData(sdata1, sdata2, false);
    if (!ok) {
      System.out.printf("%s%n", f);
    }
    return ok;
  }

}
