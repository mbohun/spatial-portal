/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ala.spatial.data;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.ala.spatial.sampling.Sampling;
import org.ala.spatial.util.CommonData;
import org.ala.spatial.util.Zipper;
import org.ala.spatial.wms.RecordsLookup;

/**
 *
 * @author Adam
 */
public class UploadQuery implements Query, Serializable {
   ArrayList<QueryField> data;
   double [] points;
   String name;
   String uniqueId;
   String metadata;

   /**
    *
    * @param uniqueId
    * @param name
    * @param points
    * @param fields uploaded column data in ArrayList<QueryField>.  First
    * three fields must contain record id, longitude, latitude.
    */
    public UploadQuery(String uniqueId, String name, double [] points, ArrayList<QueryField> fields, String metadata) {
        this.points = points;
        this.data = fields;
        this.name = name;
        this.uniqueId = uniqueId;
        this.metadata = metadata;
    }    

    /**
     * Get records for this query for the provided fields.
     *
     * @param fields QueryFields to return in the sample.
     * @return records as String in JSON format.
     */
    @Override
    public String sample(ArrayList<QueryField> fields) {
        StringBuilder sb = new StringBuilder();

        //do sampling
        layerSampling(fields);

        //identify fields to data for return
        int [] validFields = null;
        if(fields == null) {
            validFields = new int[data.size()];
            for(int i=0;i<data.size();i++) {
                validFields[i] = i;
            }
        } else {
            validFields = new int[fields.size()];
            for(int i=0;i<fields.size();i++) {
                validFields[i] = -1;
                for(int j=0;j<data.size();j++) {
                    if(fields.get(i) != null
                            && fields.get(i).getName() != null
                            && data.get(j).getName().equals(fields.get(i).getName())) {
                        validFields[i] = j;
                        break;
                    }
                }
            }
        }

        //header
        for(int i=0;i<validFields.length;i++) {
            if(i > 0) {
                sb.append(",");
            }
            if(validFields[i] >= 0) {
                sb.append(data.get(validFields[i]).getDisplayName());
            } else {
                sb.append(fields.get(i).getDisplayName());
            }
        }

        //data
        int recordCount = points.length/2;
        for(int j=0;j<recordCount;j++) {
            sb.append("\n");
            for(int i=0;i<validFields.length;i++) {                
                if(i > 0) {
                    sb.append(",");
                }
                if(validFields[i] >= 0 && data.get(validFields[i]).getAsString(j) != null) {
                    sb.append("\"");
                    sb.append(String.valueOf(data.get(validFields[i]).getAsString(j)).replace("\"","\"\""));
                    sb.append("\"");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Get species list for this query.
     *
     * @return species list as String containing JSON array.
     */
    @Override
    public String speciesList() {
        return null;
    }

    /**
     * Get number of occurrences in this query.
     *
     * @return number of occurrences as int or -1 on error.
     */
    @Override
    public int getOccurrenceCount() {
        return points.length/2;
    }

    /**
     * Get number of species in this query.
     *
     * @return number of species as int or -1 on error.
     */
    public int getSpeciesCount() {
        return 1;
    }

    /**
     * Get parsed coordinates and optional fields for this query.
     *
     * @param fields QueryFields to return in the sample as ArrayList<QueryField>.
     * If a QueryField isStored() it will be populated with the field data.
     * @return coordinates as double [] like [lng, lat, lng, lat, ...].
     */
    public double[] getPoints(ArrayList<QueryField> fields) {
        if(fields != null) {
            for(int i=0;i<fields.size();i++) {
                for(int j=0;j<data.size();j++) {
                    if(fields.get(i).getName().equals(data.get(j).getName())){
                        fields.get(i).copyData(data.get(j));
                        break;
                    }
                }
            }
        }

        return points;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getRank() {
        return "species";
    }

    @Override
    public String getQ() {
        return uniqueId;
    }

    @Override
    public Query newWkt(String wkt) {
        //throw new UnsupportedOperationException("Not supported yet.");
        return this;
    }

    @Override
    public Query newFacet(Facet facet) {
        //throw new UnsupportedOperationException("Not supported yet.");
        return this;
    }

     @Override
    public String getWMSpath() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public ArrayList<QueryField> getFacetFieldList() {
        return data;
    }

    /**
     * Do sampling on layers that are in fields but not in data.
     * 
     * @param fields
     */
    private void layerSampling(ArrayList<QueryField> fields) {
        if(fields == null) {
            return;
        }
        ArrayList<QueryField> forSampling = new ArrayList<QueryField>();
        for(int i=0;i<fields.size();i++) {
            boolean found = false;
            for(int j=0;j<data.size();j++) {
                if(data.get(j).getName().equals(fields.get(i).getName())) {
                    found = true;
                    break;
                }
            }
            if(!found) {
                forSampling.add(fields.get(i));
            }
        }

        //do sampling
        double [][] coordinates = new double[points.length/2][2];
        for(int i=0;i<points.length;i+=2) {
            coordinates[i/2][0] = points[i];
            coordinates[i/2][1] = points[i+1];
        }
        ArrayList<String> facetIds = new ArrayList<String>();
        for(int i=0;i<forSampling.size();i++) {
            facetIds.add(forSampling.get(i).getName());
        }
        ArrayList<String []> output = Sampling.sampling(facetIds, coordinates);

        for(int i=0;i<forSampling.size();i++) {
            String [] s = output.get(i);
            QueryField qf = forSampling.get(i);
            if(qf.getName() != null) {
                qf.ensureCapacity(s.length);
                for(int j=0;j<s.length;j++) {
                    qf.add(s[j]);
                }
                qf.store();

                data.add(qf);
            }
        }

        //update RecordsLookup for new fields
        Object[] recordData = (Object[]) RecordsLookup.getData(getQ());
        double [] points = (double[]) recordData[0];
        RecordsLookup.putData(getQ(), points, data);
    }

    @Override
    public String getSpeciesIdFieldName() {
        return null;
    }

    @Override
    public String getRecordIdFieldName() {
        return data.get(0).getName();
    }

    @Override
    public LegendObject getLegend(String colourmode) {
        //get existing
        for(int i=0;i<data.size();i++) {
            if(data.get(i).getName().equals(colourmode)) {
                return data.get(i).getLegend();
            }
        }

        //create
        ArrayList<QueryField> fields = new ArrayList<QueryField>();
        QueryField qf = new QueryField(colourmode, CommonData.getFacetLayerDisplayName(colourmode), QueryField.FieldType.AUTO);
        qf.setStored(true);
        fields.add(qf);
        layerSampling(fields);

        //get existing
        for(int i=0;i<data.size();i++) {
            if(data.get(i).getName().equals(colourmode)) {
                return data.get(i).getLegend();
            }
        }

        //fail
        return null;
    }

    public Query newFacets(List<Facet> facet){
        return this;
    }

    @Override
    public String getUrl() {
        return CommonData.webportalServer + "/ws/wms/reflect?";
    }

    List<Double> bbox = null;

    @Override
    public List<Double> getBBox() {
        if(bbox == null) {
            bbox = new ArrayList<Double>();

            double minx, miny, maxx, maxy;
            minx = points[0];
            maxx = points[0];
            miny = points[1];
            maxy = points[1];
            for(int i=0;i<points.length;i+=2) {
                if(minx > points[i]) minx = points[i];
                if(maxx < points[i]) maxx = points[i];
                if(miny > points[i + 1]) miny = points[i+1];
                if(maxy < points[i + 1]) maxy = points[i+1];
            }

            bbox.add(minx);
            bbox.add(miny);
            bbox.add(maxx);
            bbox.add(maxy);
        }
        
        return bbox;
    }

    @Override
    public String getMetadataHtml() {
        return metadata;
    }

    @Override
    public String getDownloadUrl(String [] extraFields) {
        return null;
    }

    @Override
    public byte[] getDownloadBytes(String [] extraFields) {
        ArrayList<QueryField> fields = new ArrayList<QueryField>();
        if(getFacetFieldList() != null) {
            fields.addAll(getFacetFieldList());
        }
        if(extraFields != null && extraFields.length > 0) {
            for(String s : extraFields) {
                fields.add(new QueryField(s, CommonData.getFacetLayerDisplayName(s), QueryField.FieldType.AUTO));
            }
        }
        try {
            //zip it
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(bos);
            ZipEntry ze = new ZipEntry(getName());
            zos.putNextEntry(ze);
            zos.write(sample(fields).getBytes("UTF-8"));
            zos.close();

            return bos.toByteArray();
        } catch (Exception ex) {
            Logger.getLogger(UploadQuery.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }
}

