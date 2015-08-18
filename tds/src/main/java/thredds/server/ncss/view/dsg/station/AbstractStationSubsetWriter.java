/*
 * Copyright 1998-2015 John Caron and University Corporation for Atmospheric Research/Unidata
 *
 *  Portions of this software were developed by the Unidata Program at the
 *  University Corporation for Atmospheric Research.
 *
 *  Access and use of this software shall impose the following obligations
 *  and understandings on the user. The user is granted the right, without
 *  any fee or cost, to use, copy, modify, alter, enhance and distribute
 *  this software, and any derivative works thereof, and its supporting
 *  documentation for any purpose whatsoever, provided that this entire
 *  notice appears in all copies of the software, derivative works and
 *  supporting documentation.  Further, UCAR requests that the user credit
 *  UCAR/Unidata in any publications that result from the use of this
 *  software or in any product that includes this software. The names UCAR
 *  and/or Unidata, however, may not be used in any advertising or publicity
 *  to endorse or promote any products or commercial entity unless specific
 *  written permission is obtained from UCAR/Unidata. The user also
 *  understands that UCAR/Unidata is not obligated to provide the user with
 *  any support, consulting, training or assistance of any kind with regard
 *  to the use, operation and performance of this software nor to provide
 *  the user with any updates, revisions, new versions or "bug fixes."
 *
 *  THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 *  IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 *  WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 *  DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 *  INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 *  FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 *  NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 *  WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package thredds.server.ncss.view.dsg.station;

import thredds.server.ncss.exception.FeaturesNotFoundException;
import thredds.server.ncss.exception.NcssException;
import thredds.server.ncss.params.NcssPointParamsBean;
import thredds.server.ncss.view.dsg.AbstractDsgSubsetWriter;
import thredds.server.ncss.view.dsg.FilteredPointFeatureIterator;
import ucar.ma2.StructureData;
import ucar.nc2.ft.FeatureCollection;
import ucar.nc2.ft.FeatureDatasetPoint;
import ucar.nc2.ft.PointFeature;
import ucar.nc2.ft.PointFeatureIterator;
import ucar.nc2.ft.StationTimeSeriesFeature;
import ucar.nc2.ft.StationTimeSeriesFeatureCollection;
import ucar.nc2.ft.point.StationFeature;
import ucar.nc2.ft.point.StationTimeSeriesFeatureImpl;
import ucar.nc2.ft.point.StationPointFeature;
import ucar.nc2.time.CalendarDate;
import ucar.nc2.units.DateType;
import ucar.unidata.geoloc.LatLonPoint;
import ucar.unidata.geoloc.LatLonPointImpl;
import ucar.unidata.geoloc.LatLonRect;
import ucar.unidata.geoloc.Station;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by cwardgar on 2014/05/20.
 */
public abstract class AbstractStationSubsetWriter extends AbstractDsgSubsetWriter {
    protected final StationTimeSeriesFeatureCollection stationFeatureCollection;
    protected final List<StationFeature> wantedStations;
    protected boolean headerDone = false;

    public AbstractStationSubsetWriter(FeatureDatasetPoint fdPoint, NcssPointParamsBean ncssParams)
            throws NcssException, IOException {
        super(fdPoint, ncssParams);

        List<FeatureCollection> featColList = fdPoint.getPointFeatureCollectionList();
        assert featColList.size() == 1 : "Is there ever a case when this is NOT 1?";
        assert featColList.get(0) instanceof StationTimeSeriesFeatureCollection :
                "This class only deals with StationTimeSeriesFeatureCollections.";

        this.stationFeatureCollection = (StationTimeSeriesFeatureCollection) featColList.get(0);
        this.wantedStations = getStationsInSubset(stationFeatureCollection, ncssParams);

        if (this.wantedStations.isEmpty()) {
            throw new FeaturesNotFoundException("No stations found in subset.");
        }
    }

    protected abstract void writeHeader(StationPointFeature stationPointFeat) throws Exception;

    protected abstract void writeStationPointFeature(StationPointFeature stationPointFeat) throws Exception;

    protected abstract void writeFooter() throws Exception;

    @Override
    public void write() throws Exception {

        // Perform spatial subset.
        StationTimeSeriesFeatureCollection subsettedStationFeatCol = stationFeatureCollection.subsetFeatures(wantedStations);

        subsettedStationFeatCol.resetIteration();
        try {
            while (subsettedStationFeatCol.hasNext()) {
                StationTimeSeriesFeature stationFeat = subsettedStationFeatCol.next();

                // Perform temporal subset. We do this even when a time instant is specified, in which case wantedRange
                // represents a sanity check (i.e. "give me the feature closest to the specified time, but it must at
                // least be within an hour").
                StationTimeSeriesFeature subsettedStationFeat = stationFeat.subset(wantedRange);

                if (ncssParams.getTime() != null) {
                    DateType wantedDateType = new DateType(ncssParams.getTime(), null, null);  // Parse time string.
                    CalendarDate wantedTime = wantedDateType.getCalendarDate();
                    subsettedStationFeat = new ClosestTimeStationFeatureSubset(
                            (StationTimeSeriesFeatureImpl) subsettedStationFeat, wantedTime);
                }

                writeStationTimeSeriesFeature(subsettedStationFeat);
            }
        } finally {
            subsettedStationFeatCol.finish();
        }

        writeFooter();
    }

    protected void writeStationTimeSeriesFeature(StationTimeSeriesFeature stationFeat)
            throws Exception {
        stationFeat.resetIteration();
        try {
            while (stationFeat.hasNext()) {
                PointFeature pointFeat = stationFeat.next();
                assert pointFeat instanceof StationPointFeature :
                        "Expected pointFeat to be a StationPointFeature, not a " + pointFeat.getClass().getSimpleName();

                if (!headerDone) {
                  writeHeader((StationPointFeature) pointFeat);
                  headerDone = true;
                }
                writeStationPointFeature((StationPointFeature) pointFeat);
            }
        } finally {
            stationFeat.finish();
        }
    }

    protected static class ClosestTimeStationFeatureSubset extends StationTimeSeriesFeatureImpl {
        private final StationTimeSeriesFeature stationFeat;
        private CalendarDate closestTime;

        protected ClosestTimeStationFeatureSubset(
                StationTimeSeriesFeatureImpl stationFeat, CalendarDate wantedTime) throws IOException {
            super(stationFeat, stationFeat.getTimeUnit(), stationFeat.getAltUnits(), -1);
            this.stationFeat = stationFeat;
            this.dateRange = stationFeat.getCalendarDateRange();

            long smallestDiff = Long.MAX_VALUE;

            stationFeat.resetIteration();
            try {
                while (stationFeat.hasNext()) {
                    PointFeature pointFeat = stationFeat.next();
                    CalendarDate obsTime = pointFeat.getObservationTimeAsCalendarDate();
                    long diff = Math.abs(obsTime.getMillis() - wantedTime.getMillis());

                    if (diff < smallestDiff) {
                        closestTime = obsTime;
                    }
                }
            } finally {
                stationFeat.finish();
            }
        }

        @Override
        public StructureData getFeatureData() throws IOException {
            return stationFeat.getFeatureData();
        }

        // Filter out PointFeatures that don't have the wantedTime.
        protected static class TimeFilter implements PointFeatureIterator.Filter {
            private final CalendarDate wantedTime;

            protected TimeFilter(CalendarDate wantedTime) {
                this.wantedTime = wantedTime;
            }

            @Override
            public boolean filter(PointFeature pointFeature) {
                return pointFeature.getObservationTimeAsCalendarDate().equals(wantedTime);
            }
        }

        @Override
        public PointFeatureIterator getPointFeatureIterator(int bufferSize) throws IOException {
            if (closestTime == null) {
                return stationFeat.getPointFeatureIterator(bufferSize);
            } else {
                return new FilteredPointFeatureIterator(
                        stationFeat.getPointFeatureIterator(bufferSize), new TimeFilter(closestTime));
            }
        }
    }


    // LOOK could do better : "all", and maybe HashSet<Name>
    public static List<StationFeature> getStationsInSubset(
            StationTimeSeriesFeatureCollection stationFeatCol, NcssPointParamsBean ncssParams) throws IOException {
        List<StationFeature> wantedStations;

        // verify SpatialSelection has some stations
        if (ncssParams.hasStations()) {
            List<String> stnNames = ncssParams.getStns();

            if (stnNames.get(0).equals("all")) {
                wantedStations = stationFeatCol.getStationFeatures();
            } else {
                wantedStations = stationFeatCol.getStationFeatures(stnNames);
            }
        } else if (ncssParams.hasLatLonBB()) {
            LatLonRect llrect = ncssParams.getLatLonBoundingBox();
            wantedStations = stationFeatCol.getStationFeatures(llrect);
        } else if (ncssParams.hasLatLonPoint()) {
            Station closestStation = findClosestStation(
                    stationFeatCol, new LatLonPointImpl(ncssParams.getLatitude(), ncssParams.getLongitude()));
            List<String> stnList = new ArrayList<>();
            stnList.add(closestStation.getName());
            wantedStations = stationFeatCol.getStationFeatures(stnList);
        } else { // Want all.
            wantedStations = stationFeatCol.getStationFeatures();
        }

        return wantedStations;
    }

    /*
     * Find the station closest to the specified point.
     * The metric is (lat-lat0)**2 + (cos(lat0)*(lon-lon0))**2
     *
     * @param lat latitude value
     * @param lon longitude value
     * @return name of station closest to the specified point
     * @throws IOException if read error
     */
    public static Station findClosestStation(StationTimeSeriesFeatureCollection stationFeatCol, LatLonPoint pt)
            throws IOException {
        double lat = pt.getLatitude();
        double lon = pt.getLongitude();
        double cos = Math.cos(Math.toRadians(lat));
        List<Station> stations = stationFeatCol.getStations();
        Station min_station = stations.get(0);
        double min_dist = Double.MAX_VALUE;

        for (Station s : stations) {
            double lat1 = s.getLatitude();
            double lon1 = LatLonPointImpl.lonNormal(s.getLongitude(), lon);
            double dy = Math.toRadians(lat - lat1);
            double dx = cos * Math.toRadians(lon - lon1);
            double dist = dy * dy + dx * dx;
            if (dist < min_dist) {
                min_dist = dist;
                min_station = s;
            }
        }
        return min_station;
    }
}
