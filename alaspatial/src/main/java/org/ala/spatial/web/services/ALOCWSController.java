package org.ala.spatial.web.services;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Hashtable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import org.ala.spatial.analysis.service.AlocService;
import org.ala.spatial.analysis.service.LayerImgService;
import org.ala.spatial.util.Grid;
import org.ala.spatial.util.Layer;
import org.ala.spatial.util.SimpleRegion;
import org.ala.spatial.util.SpatialSettings;
import org.ala.spatial.util.TabulationSettings;
import org.ala.spatial.util.UploadSpatialResource;
import org.ala.spatial.util.Zipper;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 *
 * @author ajay
 */
@Controller
@RequestMapping("/ws/aloc/")
public class ALOCWSController {

    private SpatialSettings ssets;
	private double xMin;
	private double yMin;
	private double xRes;
	private double yRes;
  
    @RequestMapping(value = "/process", method = RequestMethod.GET)
    public
    @ResponseBody
    String process(HttpServletRequest req) {
        String pid = "";
        try {
            TabulationSettings.load();

            long currTime = System.currentTimeMillis();

            String currentPath = req.getSession(true).getServletContext().getRealPath("/");
            String outputpath = currentPath + "output/aloc/" + currTime + "/";
            String outputfile = outputpath + "aloc.png";
            File fDir = new File(outputpath);
            fDir.mkdir();

            ssets = new SpatialSettings();

            String groupCount = req.getParameter("gc");
            Layer[] envList = getEnvFilesAsLayers(req.getParameter("envlist"));
           
            AlocService.run(outputfile, envList, Integer.parseInt(groupCount),null,Long.toString(currTime));

            generateWorldFiles(outputpath);

            Hashtable htGeoserver = ssets.getGeoserverSettings();
            String url = (String) htGeoserver.get("geoserver_url") + "/rest/workspaces/ALA/coveragestores/aloc_" + currTime + "/file.worldimage?coverageName=aloc_class_" + currTime;
            String extra = "";
            String username = (String) htGeoserver.get("geoserver_username");
            String password = (String) htGeoserver.get("geoserver_password");

            String[] files = new String[]{
                outputpath + "aloc.png",
                outputpath + "aloc.pgw",
                outputpath + "aloc.prj"
            };

            String pngZipFile = outputpath + "aloc.zip";
            Zipper.zipFiles(files, pngZipFile);
            // Upload the file to GeoServer using REST calls
            System.out.println("Uploading file: " + pngZipFile + " to \n" + url);
            UploadSpatialResource.loadResource(url, extra, username, password, pngZipFile);
            
            /* create style */
            UploadSpatialResource.httpCall(UploadSpatialResource.POST
            		,(String) htGeoserver.get("geoserver_url") + "/rest/styles"
            		,username,password
            		,outputfile+".xml","text/xml");
        	
        	/* upload style */
            UploadSpatialResource.httpCall(UploadSpatialResource.PUT
            		,(String) htGeoserver.get("geoserver_url") + "/rest/styles/aloc_" + currTime
            		,username,password
            		,outputfile+".sld","application/vnd.ogc.sld+xml");
            
            // if it doesn't die by now, then all is good
            // set the pid and sent the response back to the client 
            
            /* register with LayerImgService */
            String extents = "252\n210\n112.083333333335\n-9.083333333335\n154.083333333335\n-44.083333333335";
            StringBuffer legend = new StringBuffer();
            System.out.println("legend path:" + outputpath + "aloc.png.csv");
            BufferedReader flegend = new BufferedReader(new FileReader(outputpath + "aloc.png.csv"));
            String line;
            while((line = flegend.readLine()) != null) {
            	legend.append(line);
            	legend.append("\r\n");
            }
            flegend.close();
            System.out.println("registering layer image (A)");
            if (!LayerImgService.registerLayerImage(currentPath, pid, outputfile, extents, legend.toString())) {
            	//error
            }
            
            pid = "" + currTime;
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return pid;
    }
    
    @RequestMapping(value = "/processgeo", method = RequestMethod.GET)
    public
    @ResponseBody
    String processgeo(HttpServletRequest req) {
        String pid = "";
        try {
            TabulationSettings.load();
            
            long currTime = System.currentTimeMillis();

            String currentPath = req.getSession(true).getServletContext().getRealPath("/");
            String outputpath = currentPath + "output/aloc/" + currTime + "/";
            String outputfile = outputpath + "aloc.png";
            File fDir = new File(outputpath);
            fDir.mkdir();

            ssets = new SpatialSettings();

            String groupCount = req.getParameter("gc");
            Layer[] envList = getEnvFilesAsLayers(req.getParameter("envlist"));
            SimpleRegion simpleregion = SimpleRegion.parseSimpleRegion(req.getParameter("points"));
           
            AlocService.run(outputfile, envList, Integer.parseInt(groupCount),simpleregion,Long.toString(currTime));
            
            generateWorldFiles(outputpath);

            Hashtable htGeoserver = ssets.getGeoserverSettings();
            String url = (String) htGeoserver.get("geoserver_url") + "/rest/workspaces/ALA/coveragestores/aloc_" + currTime + "/file.worldimage?coverageName=aloc_class_" + currTime;
            String extra = "";
            String username = (String) htGeoserver.get("geoserver_username");
            String password = (String) htGeoserver.get("geoserver_password");

            String[] files = new String[]{
                outputpath + "aloc.png",
                outputpath + "aloc.pgw",
                outputpath + "aloc.prj"
            };

            String pngZipFile = outputpath + "aloc.zip";
            Zipper.zipFiles(files, pngZipFile);
            // Upload the file to GeoServer using REST calls
            System.out.println("Uploading file: " + pngZipFile + " to \n" + url);
            UploadSpatialResource.loadResource(url, extra, username, password, pngZipFile);
            
            try{
            	FileWriter fw = new FileWriter(outputfile + ".xml");
            	fw.append("<style><name>aloc_" + currTime + "</name><filename>aloc.png.sld</filename></style>");
            	fw.close();
            	
            	fw = new FileWriter(outputfile + "_.xml");
            	fw.append("<layer><defaultStyle><name>aloc_" + currTime + "</name></defaultStyle></layer>");
            	fw.close();
            }catch(Exception e){
            	e.printStackTrace();
            }
        	// .xml fw.append("
        	// .2.xml fw.append(
        	
            /* create style */
            UploadSpatialResource.httpCall(UploadSpatialResource.POST
            		,(String) htGeoserver.get("geoserver_url") + "/rest/styles"
            		,username,password
            		,outputfile+".xml","text/xml");
        	
        	/* upload style */
            UploadSpatialResource.httpCall(UploadSpatialResource.PUT
            		,(String) htGeoserver.get("geoserver_url") + "/rest/styles/aloc_" + currTime
            		,username,password
            		,outputfile+".sld","application/vnd.ogc.sld+xml");
            
            /* register with LayerImgService */
            String extents = "252\n210\n112.083333333335\n-9.083333333335\n154.083333333335\n-44.083333333335";
            
            StringBuffer legend = new StringBuffer();
            System.out.println("legend path:" + outputpath + "aloc.png.csv");
            BufferedReader flegend = new BufferedReader(new FileReader(outputpath + "aloc.png.csv"));
            String line;
            while((line = flegend.readLine()) != null) {
            	legend.append(line);
            	legend.append("\r\n");
            }
            flegend.close();
            System.out.println("registering layer image (A): pid=" + currTime);
            if (!LayerImgService.registerLayerImage(currentPath, "" + currTime, outputfile, extents, legend.toString())) {
            	//error
            }
            

            pid = "" + currTime;
        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
        return pid;
    }

    private void generateWorldFiles(String outputpath) {
        try {

            StringBuffer sbWorldFile = new StringBuffer();
            //  pixel X size
            // sbWorldFile.append(xRes).append("\n");
            sbWorldFile.append("0.16666666667").append("\n");
            // rotation about the Y axis (usually 0.0)
            sbWorldFile.append("0").append("\n");
            // rotation about the X axis (usually 0.0)
            sbWorldFile.append("0").append("\n");
            // negative pixel Y size
            // sbWorldFile.append(yRes).append("\n");
            sbWorldFile.append("-0.16666666667").append("\n");
            // X coordinate of upper left pixel center
            // sbWorldFile.append(xMin).append("\n");
            sbWorldFile.append("112.083333333335").append("\n");
            // Y coordinate of upper left pixel center
            // sbWorldFile.append(yMin).append("\n");
            sbWorldFile.append("-9.083333333335").append("\n");

            StringBuffer sbProjection = new StringBuffer();
            sbProjection.append("GEOGCS[\"WGS 84\", ").append("\n");
            sbProjection.append("    DATUM[\"WGS_1984\", ").append("\n");
            sbProjection.append("        SPHEROID[\"WGS 84\",6378137,298.257223563, ").append("\n");
            sbProjection.append("            AUTHORITY[\"EPSG\",\"7030\"]], ").append("\n");
            sbProjection.append("        AUTHORITY[\"EPSG\",\"6326\"]], ").append("\n");
            sbProjection.append("    PRIMEM[\"Greenwich\",0, ").append("\n");
            sbProjection.append("        AUTHORITY[\"EPSG\",\"8901\"]], ").append("\n");
            sbProjection.append("    UNIT[\"degree\",0.01745329251994328, ").append("\n");
            sbProjection.append("        AUTHORITY[\"EPSG\",\"9122\"]], ").append("\n");
            sbProjection.append("    AUTHORITY[\"EPSG\",\"4326\"]] ").append("\n");

            PrintWriter pgwout = new PrintWriter(new BufferedWriter(new FileWriter(outputpath + "aloc.pgw")));
            pgwout.write(sbWorldFile.toString());
            pgwout.close();

            PrintWriter prjout = new PrintWriter(new BufferedWriter(new FileWriter(outputpath + "aloc.prj")));
            prjout.write(sbProjection.toString());
            prjout.close();



        } catch (Exception e) {
            e.printStackTrace(System.out);
        }

    }

    private Layer[] getEnvFilesAsLayers(String envNames) {
        try {
            System.out.println("envNames.pre: " + envNames);
            envNames = URLDecoder.decode(envNames, "UTF-8");
            System.out.println("envNames.post: " + envNames);
        } catch (UnsupportedEncodingException ex) {
            ex.printStackTrace(System.out);
        }
        String[] nameslist = envNames.split(":");
        Layer[] sellayers = new Layer[nameslist.length];

        Layer[] _layerlist = ssets.getEnvironmentalLayers();
        String _layerPath = ssets.getEnvDataPath();


        for (int j = 0; j < nameslist.length; j++) {
            for (int i = 0; i < _layerlist.length; i++) {
                if (_layerlist[i].display_name.equalsIgnoreCase(nameslist[j])) {
                    sellayers[j] = _layerlist[i];
                    //sellayers[j].name = _layerPath + sellayers[j].name;
                    System.out.println("Adding layer for ALOC: " + sellayers[j].name);
                    continue;
                }
            }
        }

        // while we are here, lets loads the values for the metadata
        Grid g = new Grid(sellayers[0].name);
        xMin = g.xmin;
        yMin = g.ymax;
        xRes = g.xres;
        yRes = g.yres;

        return sellayers;
    }
    
   
}