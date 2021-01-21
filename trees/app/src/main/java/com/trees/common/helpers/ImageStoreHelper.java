package com.trees.common.helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;

public class ImageStoreHelper {
    private static final String LOG_TAG = "AMELIA";
    private enum Filetype {
            TOF,
            JPEG,
            MATRIX,
    }
    // path for storing data
    private static String filepath =
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Tree";
    private static final String FOLDER = "/samples";
    private static final String PREFIX = "Capture_Sample_";


    private static File getOrCreateFile(String filename) {
        // Open the output file
        // As recommended by:
        // https://stackoverflow.com/questions/44587187/android-how-to-write-a-file-to-internal-storage
        File dir = new File(filepath, FOLDER);
        Log.i(LOG_TAG, dir.getAbsolutePath());

        File outFile = new File(dir, filename);
        if (!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }
        return outFile;
    }

    private static String getFileName(Integer sample_num, Integer capture_num, Filetype ftype) {
        String suffix;
        switch (ftype) {
            case TOF:
                suffix = "";
                break;
            case JPEG:
                suffix = ".jpeg";
                break;
            case MATRIX:
                suffix = ".txt";
                break;
            default:
                suffix = "";
                break;
        }
        return PREFIX + sample_num + "_" + capture_num + suffix;

    }

    public static void saveToFileTOF(
            Integer sampleNumber, Integer captureNumber,
            ArrayList<Short> xBuffer, ArrayList<Short> yBuffer,
            ArrayList<Float> dBuffer, ArrayList<Float> percentageBuffer) throws IOException {

        String filename = getFileName(sampleNumber, captureNumber, Filetype.TOF);
        File outFile = getOrCreateFile(filename);
        // Write the TOF data currently in buffers to an output file.
        Log.i(LOG_TAG, "Writing to the file");

        // Write to the output file
        try (FileWriter writer = new FileWriter(outFile)) {
            StringBuilder str = new StringBuilder();

            for (int i = 0; i < dBuffer.size(); i++) {
                str.append(xBuffer.get(i));
                str.append(',');
                str.append(yBuffer.get(i));
                str.append(',');
                str.append(dBuffer.get(i));
                str.append(',');
                str.append(percentageBuffer.get(i));
                str.append('\n');
            }
            writer.write(str.toString());
            writer.flush();
            Log.i(LOG_TAG, "Successfully wrote the file " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveToFileRGB(
            Integer sampleNumber, Integer captureNumber, Image image) throws IOException {

        String filename = getFileName(sampleNumber, captureNumber, Filetype.JPEG);
        File outFile = getOrCreateFile(filename);

        // Write the TOF data currently in buffers to an output file.
        Log.i(LOG_TAG, "Writing to the file");

        byte[] imageBytes = ImageUtil.imageToByteArray(image);

        Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        try {
            FileOutputStream out = new FileOutputStream(outFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void saveToFileMatrix(
            Integer sampleNumber, Integer captureNumber,
            float[] projectionMatrix, float[] viewMatrix) throws IOException {

        String filename = getFileName(sampleNumber, captureNumber, Filetype.MATRIX);
        File outFile = getOrCreateFile(filename);
        // Write the TOF data currently in buffers to an output file.
        Log.i(LOG_TAG, "Writing to the file");

        try {
            DecimalFormat df = new DecimalFormat("#.##########");
            df.setRoundingMode(RoundingMode.CEILING);
            PrintWriter out = new PrintWriter(outFile);
            for (int i = 0; i < projectionMatrix.length; i++) {
                if (i != 0) {
                    out.printf(", ");
                }
                out.printf(df.format(projectionMatrix[i]));
            }
            out.printf("\n");

            for (int i = 0; i < viewMatrix.length; i++) {
                if (i != 0) {
                    out.printf(", ");
                }
                out.printf(df.format(viewMatrix[i]));
            }
            out.printf("\n");
            out.close();
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static void deleteFiles() {
        // File object for the directory where the data is saved.
        File dir = new File(filepath, FOLDER);
        if (!dir.exists()) {
            return;
        }

        // Clean up the directory
            for (File file : dir.listFiles()) {
            file.delete();
        }
}


}
