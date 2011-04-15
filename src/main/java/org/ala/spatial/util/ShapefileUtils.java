package org.ala.spatial.util;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.WKTReader;
import com.vividsolutions.jts.io.WKTWriter;
import java.io.File;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import javax.swing.filechooser.FileNameExtensionFilter;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureSource;
import org.geotools.data.FileDataStore;
import org.geotools.data.FileDataStoreFinder;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.FeatureCollections;
import org.geotools.feature.FeatureIterator;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

/**
 *
 * @author ajay
 */
public class ShapefileUtils {

    public static Map loadShapefile(File shpfile) {
        try {

            FileDataStore store = FileDataStoreFinder.getDataStore(shpfile);

            System.out.println("Loading shapefile. Reading content:");
            System.out.println(store.getTypeNames()[0]);

            FeatureSource featureSource = store.getFeatureSource(store.getTypeNames()[0]);

            FeatureCollection featureCollection = featureSource.getFeatures();
            FeatureIterator it = featureCollection.features();
            Map shape = new HashMap(); 
            while (it.hasNext()) {
                //System.out.println("======================================");
                //System.out.println("Feature: ");
                SimpleFeature feature = (SimpleFeature) it.next();
                //System.out.println(feature.getID());
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                WKTWriter wkt = new WKTWriter();
                //System.out.println(wkt.writeFormatted(geom));
                //addWKTLayer(wkt.write(geom), feature.getID());
                shape.put("id", feature.getID());
                shape.put("wkt", wkt.write(geom));
                break;
            }
            featureCollection.close(it);

            return shape; 

        } catch (Exception e) {
            System.out.println("Unable to load shapefile: ");
            e.printStackTrace(System.out);
        }

        return null; 
    }

    public static void saveShapefile(File shpfile, String wktString) {
        try {
            final SimpleFeatureType TYPE = createFeatureType(); 

            FeatureCollection collection = FeatureCollections.newCollection();
            SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(TYPE);

            WKTReader wkt = new WKTReader();
            Geometry geom = wkt.read(wktString);
            featureBuilder.add(geom);

            SimpleFeature feature = featureBuilder.buildFeature(null);
            collection.add(feature);

            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

            Map<String, Serializable> params = new HashMap<String, Serializable>();
            params.put("url", shpfile.toURI().toURL());
            params.put("create spatial index", Boolean.TRUE);

            ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createNewDataStore(params);
            newDataStore.createSchema(TYPE);

            newDataStore.forceSchemaCRS(DefaultGeographicCRS.WGS84);

            Transaction transaction = new DefaultTransaction("create");

            String typeName = newDataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);

            if (featureSource instanceof SimpleFeatureStore) {
                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;

                featureStore.setTransaction(transaction);
                try {
                    featureStore.addFeatures(collection);
                    transaction.commit();

                } catch (Exception problem) {
                    problem.printStackTrace();
                    transaction.rollback();

                } finally {
                    transaction.close();
                }
            }

            System.out.println("Active Area shapefile written to: " + shpfile.getAbsolutePath());

        } catch (Exception e) {
            System.out.println("Unable to save shapefile: ");
            e.printStackTrace(System.out);
        }
    }

    private static SimpleFeatureType createFeatureType() {

        // DataUtilities.createType("ActiveArea", "area:Polygon:srid=4326", "name:String");

        SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
        builder.setName("ActiveArea");
        builder.setCRS(DefaultGeographicCRS.WGS84); // <- Coordinate reference system

        // add attributes in order
        builder.add("area", Polygon.class);
        builder.length(15).add("name", String.class); // <- 15 chars width for name field

        // build the type
        final SimpleFeatureType ActiveArea = builder.buildFeatureType();

        return ActiveArea;
    }

    public static void main(String[] args) {
        //System.out.println("Loading shapefile");
        //File shpfile = new File("/Users/ajay/projects/tmp/uploads/SinglePolygon/SinglePolygon.shp");
        //loadShapefile(shpfile);

        System.out.println("Saving shapefile");
        File shpfile = new File("/Users/ajay/projects/tmp/uploads/ActiveArea.shp");
        String wktString = "POLYGON((128.63867187521 -23.275665408794,128.63867187521 -29.031198581005,137.25195312487 -28.56908637161,138.8339843748 -24.561114535569,134.61523437497 -20.83211850342,130.83593750012 -21.160338084068,128.63867187521 -23.275665408794))";
        saveShapefile(shpfile,wktString);

    }

}
