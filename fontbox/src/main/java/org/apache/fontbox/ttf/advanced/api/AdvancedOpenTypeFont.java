package org.apache.fontbox.ttf.advanced.api;

import java.io.IOException;
import java.nio.IntBuffer;

import org.apache.fontbox.ttf.CFFTable;
import org.apache.fontbox.ttf.CmapLookup;
import org.apache.fontbox.ttf.OTLTable;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.apache.fontbox.ttf.TTFDataStream;
import org.apache.fontbox.ttf.TTFTable;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.fontbox.ttf.advanced.GlyphDefinitionTable;
import org.apache.fontbox.ttf.advanced.GlyphSubstitutionTable;
import org.apache.fontbox.ttf.advanced.GlyphVectorAdvanced;
import org.apache.fontbox.ttf.advanced.GlyphVectorSimple;
import org.apache.fontbox.ttf.advanced.util.GlyphSequence;

public class AdvancedOpenTypeFont extends OpenTypeFont {

    AdvancedOpenTypeFont(TTFDataStream fontData) {
        super(fontData);
    }

    /**
     * Get the "GSUB" table for this OTF.
     *
     * @return The "GSUB" table.
     */
    public org.apache.fontbox.ttf.advanced.GlyphSubstitutionTable getGSUB() throws IOException 
    {
        return (org.apache.fontbox.ttf.advanced.GlyphSubstitutionTable) getTable(org.apache.fontbox.ttf.advanced.GlyphSubstitutionTable.TAG);
    }

    /**
     * Get the "GDEF" table for this OTF.
     *
     * @return The "GDEF" table.
     */
    public org.apache.fontbox.ttf.advanced.GlyphDefinitionTable getGDEF() throws IOException
    {
        return (org.apache.fontbox.ttf.advanced.GlyphDefinitionTable) getTable(org.apache.fontbox.ttf.advanced.GlyphDefinitionTable.TAG);
    }

     /**
     * Get the "GPOS" table for this OTF.
     *
     * @return The "GPOS" table.
     */
    public org.apache.fontbox.ttf.advanced.GlyphPositioningTable getGPOS() throws IOException
    {
        return (org.apache.fontbox.ttf.advanced.GlyphPositioningTable) getTable(org.apache.fontbox.ttf.advanced.GlyphPositioningTable.TAG);
    }

    @Override
    public org.apache.fontbox.ttf.GlyphSubstitutionTable getGsub() throws IOException {
        return null;
    }

    /**
     * TODO
     */
    private float getNormalizedWidth(float width) throws IOException {
        float unitsPerEM = getUnitsPerEm();
        if (Float.compare(unitsPerEM, 1000) != 0)
        {
            width *= 1000f / unitsPerEM;
        }
        return width;
    }

    /**
     * TODO
     */
    public GlyphVector createGlyphVector(String text) throws IOException 
    {
        if (text.isEmpty()) {
            return new GlyphVectorSimple(null);
        }

        int[] codePoints = text.codePoints().toArray();
        int[] originalGlyphs = new int[codePoints.length];

        // TODO: What if cmap is null?
        // TODO: What if glyph for character does not exist?
        CmapLookup cmapLookup = getUnicodeCmapLookup();

        for (int i = 0; i < codePoints.length; i++) {
            originalGlyphs[i] = cmapLookup.getGlyphId(codePoints[i]);
        }
//System.out.println("original glyphs = " + Arrays.toString(originalGlyphs));

        IntBuffer characters = IntBuffer.wrap(codePoints);
        IntBuffer glyphs = IntBuffer.wrap(originalGlyphs);

        GlyphSequence sequence = new GlyphSequence(characters, glyphs, null);

        org.apache.fontbox.ttf.advanced.GlyphSubstitutionTable substitutionTable =
          (org.apache.fontbox.ttf.advanced.GlyphSubstitutionTable) getGSUB();

        org.apache.fontbox.ttf.advanced.GlyphPositioningTable positioningTable =
          (org.apache.fontbox.ttf.advanced.GlyphPositioningTable) getGPOS();

        // org.apache.fontbox.ttf.advanced.GlyphDefinitionTable gdefTable =
        //   (org.apache.fontbox.ttf.advanced.GlyphDefinitionTable) getGDEF();

        // if (gdefTable != null) {
        //     sequence = gdefTable.reorderCombiningMarks(sequence, widths, gpa, script, language, features);
        // }

        Object[][] extraFeatures = new Object[][] {
            //new Object[] { "smcp", Boolean.FALSE },
            //new Object[] { "frac", Boolean.TRUE }
        };
        //extraFeatures = null;

        // TODO: Correct script and language
        GlyphSequence substituted = substitutionTable != null ? 
             substitutionTable.substitute(sequence, "latn", "dflt", extraFeatures) : sequence;

        int[][] adjustments = null;
        int[] widths = null;
        boolean positioned = false;

        if (positioningTable != null) {
            int size = substituted.getGlyphCount();
            adjustments = new int[size][4];
            widths = new int[size];
            int[] workingGlyphs = substituted.getGlyphArray(false);

            for (int i = 0; i < size; i++) {
                widths[i] = getAdvanceWidth(workingGlyphs[i]);
            }

            // TODO: Correct script, language and font size
            // TODO: widths?
            positioned = positioningTable != null ? positioningTable.position(
                substituted, "latn", "dflt", null, 20, widths, adjustments) : false;
        }

//System.out.println("widths = " + Arrays.toString(widths));
        int[] outGlyphs = substituted.getGlyphArray(false);
//System.out.println("outGlyphs = " + Arrays.toString(outGlyphs));
        int outSize = substituted.getGlyphCount();

        float width = 0f;
        for (int i = 0; i < outSize; i++) {
            int xAdjust = 0;

            if (positioned) {
                int[] glyphAdjust = adjustments[i];
//System.out.println("xAdjust = " + Arrays.toString(glyphAdjust));

                int placementX = glyphAdjust[0];
                int placementY = glyphAdjust[1];
                int advanceX = glyphAdjust[2];
                int advanceY = glyphAdjust[3];

                if (placementX != 0 || advanceX != 0) {
                    xAdjust = advanceX; // + placementX;
                }
            }


//System.out.println("advance = " + getAdvanceWidth(outGlyphs[i]));

            // TODO: Something with yAdjust
            // TODO: Debug width

            width += getAdvanceWidth(outGlyphs[i]) + xAdjust;
        }

        //System.out.println("w = " + width);

        return new GlyphVectorAdvanced(outGlyphs, getNormalizedWidth(width), positioned ? adjustments : null);
    }

    /**
     * Returns true if this font uses OpenType Layout (Advanced Typographic) tables.
     */
    public boolean hasLayoutTables()
    {
        return tables.containsKey("BASE") ||
               tables.containsKey("GDEF") ||
               tables.containsKey("GPOS") ||
               tables.containsKey("GSUB") ||
               tables.containsKey("JSTF");
    }

}
