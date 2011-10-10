package org.ala.spatial.analysis.web;

import au.org.emii.portal.composer.MapComposer;
import au.org.emii.portal.menu.MapLayer;
import au.org.emii.portal.settings.SettingsSupplementary;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.ala.spatial.util.CommonData;
import org.ala.spatial.util.UserData;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.event.InputEvent;
import org.zkoss.zul.Combobox;
import org.zkoss.zul.Comboitem;

/**
 *
 * @author ajay
 */
public class SpeciesAutoComplete extends Combobox {

    private boolean bSearchCommon = false;
    private SettingsSupplementary settingsSupplementary = null;

    public boolean isSearchCommon() {
        return bSearchCommon;
    }

    public void setSearchCommon(boolean searchCommon) {
        this.bSearchCommon = searchCommon;
    }

    public SpeciesAutoComplete() {
        refresh(""); //init the child comboitems
    }

    public SpeciesAutoComplete(String value) {
        super(value); //it invokes setValue(), which inits the child comboitems
    }

    @Override
    public void setValue(String value) {
        super.setValue(value);
    }

    /** Listens what an user is entering.
     */
    public void onChanging(InputEvent evt) {
        if (!evt.isChangingBySelectBack()) {
            refresh(evt.getValue());
        }
    }

    /** Refresh comboitem based on the specified value.
     */
    public void refresh(String val) {

        // Start by constraining the search to a min 3-chars
        if (val.length() < 3) {
            return;
        }

        if (settingsSupplementary != null) {
        } else if (this.getParent() != null) {
            settingsSupplementary = this.getThisMapComposer().getSettingsSupplementary();
        } else {
            return;
        }

        //Do something about geocounts.
        //High limit because geoOnly=true cuts out valid matches, e.g. "Macropus"
        //String snUrl = CommonData.bieServer + "/search.json?limit=20&q=";

        try {

            System.out.println("Looking for common name: " + isSearchCommon());

            getItems().clear();     
            Iterator it = getItems().iterator();
            if (val.length() == 0) {
                Comboitem myci = null;
                if (it != null && it.hasNext()) {
                    myci = ((Comboitem) it.next());
                    myci.setLabel("Please start by typing in a species name...");
                } else {
                    it = null;
                    myci = new Comboitem("Please start by typing in a species name...");
                    myci.setParent(this);
                }
                myci.setDescription("");
                myci.setDisabled(true);
            } else {
                StringBuilder sb = new StringBuilder();

                //sb.append(searchService(val));
                sb.append(autoService(val));
                //sb.append(loadUserPoints(val));

                String sslist = sb.toString();
                System.out.println("SpeciesAutoComplete: \n" + sslist);

                String[] aslist = sslist.split("\n");

                if (aslist.length > 0 && aslist[0].length() > 0) {

                    Arrays.sort(aslist);

                    for (int i = 0; i < aslist.length; i++) {
                        String[] spVal = aslist[i].split("/");

                        String taxon = spVal[0].trim();

                        Comboitem myci = null;
                        if (it != null && it.hasNext()) {
                            myci = ((Comboitem) it.next());
                            myci.setLabel(taxon);
                        } else {
                            it = null;
                            myci = new Comboitem(taxon);
                            myci.setParent(this);
                        }

                        String[] wmsNames = CommonData.getSpeciesDistributionWMS(spVal[1]);
                        if (wmsNames != null && wmsNames.length > 0) {
                            if (wmsNames.length == 1) {
                                myci.setDescription(spVal[2] + " - " + spVal[3] + " records + map");
                            } else {
                                myci.setDescription(spVal[2] + " - " + spVal[3] + " records + " + wmsNames.length + " maps");
                            }
                        } else {
                            myci.setDescription(spVal[2] + " - " + spVal[3] + " records");
                        }

                        myci.setDisabled(false);
                        if(myci.getAnnotations() != null) {
                            myci.getAnnotations().clear();
                        }
                        myci.addAnnotation(spVal[1], "LSID", null);

                        myci.setValue(taxon);
                    }
                }

            }
            while (it != null && it.hasNext()) {
                it.next();
                it.remove();
            }

        } catch (Exception e) {
            System.out.println("Oopss! something went wrong in SpeciesAutoComplete.refresh");
            e.printStackTrace(System.out);
        }

    }

    private MapComposer getThisMapComposer() {
        MapComposer mapComposer = null;
        Page page = getPage();
        mapComposer = (MapComposer) page.getFellow("mapPortalPage");

        return mapComposer;
    }

    private String loadUserPoints(String val) {
        String userPoints = "";
        Hashtable<String, UserData> htUserSpecies = (Hashtable) getThisMapComposer().getSession().getAttribute("userpoints");
        val = val.toLowerCase();

        try {
            if (htUserSpecies != null) {
                if (htUserSpecies.size() > 0) {
                    Enumeration e = htUserSpecies.keys();
                    StringBuilder sbup = new StringBuilder();
                    while (e.hasMoreElements()) {
                        String k = (String) e.nextElement();
                        UserData ud = htUserSpecies.get(k);

                        if ("user".contains(val)
                                || ud.getName().toLowerCase().contains(val)
                                || ud.getDescription().toLowerCase().contains(val)) {
                            sbup.append(ud.getName());
                            sbup.append(" /");
                            sbup.append(k);
                            sbup.append("/");
                            sbup.append("user");
                            sbup.append("/");
                            sbup.append(ud.getFeatureCount());
                            sbup.append("\n");
                        }
                    }
                    userPoints = sbup.toString();
                }
            }
        } catch (Exception e) {
            System.out.println("Unable to load user points into Species Auto Complete");
            e.printStackTrace(System.out);
        }

        return userPoints;
    }

    String searchService(String val) throws Exception {
        String nsurl = CommonData.bieServer + "/search.json?pageSize=100&q=" + URLEncoder.encode(val, "UTF-8");

        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod(nsurl);
        get.addRequestHeader("Content-type", "text/plain");

        int result = client.executeMethod(get);
        String rawJSON = get.getResponseBodyAsString();

        //parse
        JSONObject jo = JSONObject.fromObject(rawJSON);
        jo = jo.getJSONObject("searchResults");

        StringBuilder slist = new StringBuilder();
        JSONArray ja = jo.getJSONArray("results");
        for(int i=0;i<ja.size();i++){
            JSONObject o = ja.getJSONObject(i);

            //count for guid
            try {
                long count = CommonData.lsidCounts.getCount(o.getLong("left"), o.getLong("right"));

                if(count > 0 && o.containsKey("name") && o.containsKey("guid") && o.containsKey("rank")) {
                    if(slist.length() > 0) {
                        slist.append("\n");
                    }

                    String commonName = null;
                    boolean commonNameMatch = false;
                    if(o.containsKey("commonName") && !o.getString("commonName").equals("none") && !o.getString("commonName").equals("null")) {
                        commonName = o.getString("commonName");
                        commonName = commonName.trim().replace("/",",");
                        String [] cns  = commonName.split(",");
                        String st = val.toLowerCase();
                        for(int j=0;j<cns.length;j++) {
                            if(cns[j].toLowerCase().contains(val)) {
                                commonName = cns[j];
                                commonNameMatch = true;
                                break;
                            }
                        }
                        if(commonName.indexOf(',') > 1) {
                            commonName = commonName.substring(0, commonName.indexOf(','));
                        }
                    }

                    //macaca / urn:lsid:catalogueoflife.org:taxon:d84852d0-29c1-102b-9a4a-00304854f820:ac2010 / genus / found 17
                    //swap name and common name if it is a common name match
                    if(commonNameMatch) {
                        slist.append(commonName).append(" /");
                        slist.append(o.getString("guid")).append("/");
                        slist.append(o.getString("rank"));
                        slist.append(", ").append(o.getString("name").replace("/",","));
                        slist.append("/found ");
                        slist.append(count);
                    } else {
                        slist.append(o.getString("name").replace("/",",")).append(" /");
                        slist.append(o.getString("guid")).append("/");
                        slist.append(o.getString("rank"));
                        if(commonName != null) slist.append(", ").append(commonName);
                        slist.append("/found ");
                        slist.append(count);
                    }
                }
            } catch (Exception e) {

            }
        }

        return slist.toString();
    }

    String autoService(String val) throws Exception {
        //while there is inappropriate sorting use limit=50
        String nsurl = CommonData.bieServer + "/search/auto.json?limit=50&q=" + URLEncoder.encode(val, "UTF-8");

        HttpClient client = new HttpClient();
        GetMethod get = new GetMethod(nsurl);
        get.addRequestHeader("Content-type", "text/plain");

        int result = client.executeMethod(get);
        String rawJSON = get.getResponseBodyAsString();

        //parse
        JSONObject jo = JSONObject.fromObject(rawJSON);

        StringBuilder slist = new StringBuilder();
        JSONArray ja = jo.getJSONArray("autoCompleteList");

        HashSet<String> lsids = new HashSet<String>();
        for(int i=0;i<ja.size();i++){
            JSONObject o = ja.getJSONObject(i);

            //count for guid
            try {
                long count = CommonData.lsidCounts.getCount(o.getLong("left"), o.getLong("right"));

                if(count > 0 && o.containsKey("name") && o.containsKey("guid") && o.containsKey("rankString")) {
                    if(lsids.contains(o.getString("guid"))) {
                        continue;
                    }
                    lsids.add(o.getString("guid"));
                    if(slist.length() > 0) {
                        slist.append("\n");
                    }

                    String commonName = null;
                    boolean commonNameMatch = true;
                    if(o.containsKey("commonName") && !o.getString("commonName").equals("none") && !o.getString("commonName").equals("null")) {
                        commonName = o.getString("commonName");
                        commonName = commonName.trim().replace("/",",");
                        String [] cns  = commonName.split(",");
                        String st = val.toLowerCase();
                        for(int j=0;j<cns.length;j++) {
                            if(cns[j].toLowerCase().contains(val)) {
                                commonName = cns[j];
                                commonNameMatch = true;
                                break;
                            }
                        }
                        if(commonName.indexOf(',') > 1) {
                            commonName = commonName.substring(0, commonName.indexOf(','));
                        }
                    }

                    //macaca / urn:lsid:catalogueoflife.org:taxon:d84852d0-29c1-102b-9a4a-00304854f820:ac2010 / genus / found 17
                    //swap name and common name if it is a common name match
                    if(o.containsKey("commonNameMatches") && !o.getString("commonNameMatches").equals("null")) {
                        slist.append(commonName).append(" /");
                        slist.append(o.getString("guid")).append("/");
                        slist.append(o.getString("rankString"));
                        slist.append(", ").append(o.getString("name").replace("/",","));
                        slist.append("/found ");
                        slist.append(count);
                    } else {
                        slist.append(o.getString("name").replace("/",",")).append(" /");
                        slist.append(o.getString("guid")).append("/");
                        slist.append(o.getString("rankString"));
                        if(commonName != null) slist.append(", ").append(commonName);
                        slist.append("/found ");
                        slist.append(count);
                    }
                }
            } catch (Exception e) {

            }
        }

        return slist.toString();
    }
}
