/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.ala.spatial.data;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;

/**
 *
 * @author Adam
 */
public class LegendObject implements Serializable {

    Legend numericLegend;
    QueryField.FieldType fieldType;
    //categorical legend
    final public static int[] colours = {0x003366CC, 0x00DC3912, 0x00FF9900, 0x00109618, 0x00990099, 0x000099C6, 0x00DD4477, 0x0066AA00, 0x00B82E2E, 0x00316395, 0x00994499, 0x0022AA99, 0x00AAAA11, 0x006633CC, 0x00E67300, 0x008B0707, 0x00651067, 0x00329262, 0x005574A6, 0x003B3EAC, 0x00B77322, 0x0016D620, 0x00B91383, 0x00F4359E, 0x009C5935, 0x00A9C413, 0x002A778D, 0x00668D1C, 0x00BEA413, 0x000C5922, 0x00743411};
    final public static int DEFAULT_COLOUR = 0x00FFFFFF;
    final public static boolean LIMIT_LEGEND = true;
    //[0] is colour, [1] is count
    HashMap<String, int[]> categories;
    String[] categoryNameOrder;

    public LegendObject(Legend numericLegend, QueryField.FieldType fieldType) {
        this.numericLegend = numericLegend;
        this.fieldType = fieldType;
    }

    public LegendObject(String[] categories, int[] counts) {
        Object[] objects = new Object[categories.length];
        for (int i = 0; i < objects.length; i++) {
            Object[] o = new Object[2];
            o[0] = categories[i];
            o[1] = new Integer(counts[i]);
            objects[i] = o;
        }
        //sort by count in descending order
        java.util.Arrays.sort(objects, new Comparator<Object>() {

            @Override
            public int compare(Object o1, Object o2) {
                return ((Integer) ((Object[]) o2)[1])
                        - ((Integer) ((Object[]) o1)[1]);
            }
        });
        //assign colours and put in hashmap
        this.categories = new HashMap<String, int[]>();
        categoryNameOrder = new String[objects.length];
        for (int i = 0; i < objects.length; i++) {
            String category = (String) ((Object[]) objects[i])[0];
            int[] data = new int[2];
            data[0] = colours[(i < colours.length) ? i : colours.length - 1];
            data[1] = (Integer) ((Object[]) objects[i])[1];
            this.categories.put(category, data);
            categoryNameOrder[i] = category;
        }
    }

    /**
     * Get legend as a table.
     *
     * CSV
     * (header) name, red, green, blue, count CR
     * (records) string, 0-255, 0-255, 0-255, integer CR
     * 
     * @return
     */
    public String getTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("name,red,green,blue,count");
        if (numericLegend != null) {
            if (numericLegend.getMinMax() != null) { //catch manually set numeric fields without numeric data
                double[] minmax = numericLegend.getMinMax();
                double[] cutoffs = numericLegend.getCutoffdoubles();

                String format = "%.4f";
                if (fieldType == QueryField.FieldType.INT
                        || fieldType == QueryField.FieldType.LONG) {
                    format = "%.0f";
                }

                //unknown
                if (numericLegend.countOfNaN > 0) {
                    sb.append("\nUnknown,0,0,0,").append(numericLegend.countOfNaN);
                }

                if (minmax[0] == minmax[1]) {
                    String rgb = getRGB(numericLegend.getColour(minmax[0]));
                    sb.append("\n").append(String.format(format, minmax[0])).append(",").append(rgb).append(",");
                } else {
                    for (int i = -1; i < cutoffs.length; i++) {
                        if (i == -1) {
                            String rgb = getRGB(numericLegend.getColour(minmax[0]));
                            sb.append("\n>=").append(String.format(format, minmax[0])).append(",").append(rgb).append(",").append(numericLegend.groupSizesArea[0]);
                        } else if (i + 1 == cutoffs.length || (cutoffs[i] < cutoffs[i + 1] && cutoffs[i] > minmax[0])) {
                            String rgb = getRGB(numericLegend.getColour(cutoffs[i]));
                            sb.append("\n<=").append(String.format(format, cutoffs[i])).append(",").append(rgb).append(",").append(numericLegend.groupSizesArea[0]);
                        }
                    }
                }
            }
        } else {
            for (int i = 0; i < categoryNameOrder.length && i < colours.length; i++) {
                if (!LIMIT_LEGEND || i < colours.length - 1 || categoryNameOrder.length == colours.length) {
                    int[] data = categories.get(categoryNameOrder[i]);
                    String rgb = getRGB(data[0]);
                    sb.append("\n\"").append(categoryNameOrder[i].replace("\"", "\"\"")).append("\",").append(rgb).append(",").append(data[1]);
                } else {
                    int count = 0;
                    for (int j = i; j < categoryNameOrder.length; j++) {
                        int[] data = categories.get(categoryNameOrder[i]);
                        count += data[1];
                    }
                    String rgb = getRGB(colours[colours.length - 1]);
                    sb.append("\n").append(categoryNameOrder.length - i).append(" more,").append(rgb).append(",").append(count);
                }
            }
        }

        return sb.toString();
    }

    public int getColour(String value) {
        if (numericLegend != null) {
            try {
                return getColour(Double.parseDouble(value));
            } catch (Exception e) {
                return DEFAULT_COLOUR;
            }
        } else {
            int[] data = categories.get(value);

            if (data != null) {
                return data[0];
            } else {
                return DEFAULT_COLOUR;
            }
        }
    }

    public int getColour(double value) {
        if (numericLegend != null) {
            return numericLegend.getColour(value);
        } else {
            int i = (int) value;
            if (i >= 0 && i < categoryNameOrder.length) {
                return getColour(categoryNameOrder[i]);
            } else {
                return DEFAULT_COLOUR;
            }
        }
    }

    public static String getRGB(int colour) {
        return ((colour >> 16) & 0x000000ff) + ","
                + ((colour >> 8) & 0x000000ff) + ","
                + (colour & 0x000000ff);
    }

    public double[] getMinMax() {
        if (numericLegend != null) {
            return numericLegend.getMinMax();
        } else {
            double[] d = {0, categoryNameOrder.length};
            return d;
        }
    }

    public String[] getCategories() {
        return categoryNameOrder;
    }
}
