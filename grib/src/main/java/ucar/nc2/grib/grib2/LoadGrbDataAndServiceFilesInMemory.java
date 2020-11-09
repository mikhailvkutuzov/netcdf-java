package ucar.nc2.grib.grib2;

import ucar.nc2.NetcdfFile;
import ucar.nc2.NetcdfFiles;
import ucar.nc2.dataset.DatasetUrl;
import ucar.nc2.dataset.spi.NetcdfFileProvider;
import ucar.nc2.util.CancelTask;

import java.io.IOException;

public class LoadGrbDataAndServiceFilesInMemory implements NetcdfFileProvider {
    @Override
    public String getProtocol() {
        return "grib2";
    }

    @Override
    public boolean isOwnerOf(DatasetUrl durl) {
        return durl.getTrueurl().endsWith(".grb")
                || durl.getTrueurl().endsWith(".grb.ncx4")
                || durl.getTrueurl().endsWith(".grb.gbx9")
                || durl.getTrueurl().endsWith(".grb.ncx");
    }

    @Override
    public NetcdfFile open(String location, CancelTask cancelTask) throws IOException {
        return NetcdfFiles.openInMemory(location);
    }

    @Override
    public boolean isOwnerOf(String location) {
        return location.endsWith(".grb")
                || location.endsWith(".grb.ncx4")
                || location.endsWith(".grb.gbx9")
                || location.endsWith(".grb.ncx");
    }
}
