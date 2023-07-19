package com.trees.common.helpers;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Objects;

public class ImageStore implements ImageStoreInterface {
    private static final String LOG_TAG = "AMELIA";
    private enum Filetype {
        TOF,
        JPEG,
        MATRIX,
    }
    // path for storing data
    private String filepath =
            android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/Tree";
    private final String FOLDER = "/samples";
    private final String PREFIX = "Capture_Sample_";

    public ImageStore() { }

    private File getOrCreateFile(String filename) {
        File dir = new File(filepath, FOLDER);
        Log.i(LOG_TAG, dir.getAbsolutePath());

        File outFile = new File(dir, filename);
        if (!outFile.getParentFile().exists()) {
            outFile.getParentFile().mkdirs();
        }
        return outFile;
    }

    private String getFileName(Integer sample_num, Integer capture_num, Filetype ftype) {
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

    public void saveToFileTOF(
            Integer sampleNumber, Integer captureNumber, TofUtil.TofArrays arrays) throws IOException {

        String filename = getFileName(sampleNumber, captureNumber, Filetype.TOF);
        File outFile = getOrCreateFile(filename);
        Log.i(LOG_TAG, "Writing to the file");

        // Write to the output file
        try (FileWriter writer = new FileWriter(outFile)) {
            StringBuilder str = new StringBuilder();

            for (int i = 0; i < arrays.dBuffer.length; i++) {
                str.append(arrays.xBuffer[i]);
                str.append(',');
                str.append(arrays.yBuffer[i]);
                str.append(',');
                str.append(arrays.dBuffer[i]);
                str.append(',');
                str.append(arrays.percentageBuffer[i]);
                str.append('\n');
            }
            writer.write(str.toString());
            writer.flush();
            Log.i(LOG_TAG, "Successfully wrote the file " + filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void saveToFileRGB(
            Integer sampleNumber, Integer captureNumber, byte[] image) throws IOException {

        String filename = getFileName(sampleNumber, captureNumber, Filetype.JPEG);
        File outFile = getOrCreateFile(filename);

//        Bitmap bitmap = BitmapFactory.decodeByteArray(image, 0, image.length);
        try {
            FileOutputStream out = new FileOutputStream(outFile);
            out.write(image);
//            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
            out.flush();
            out.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void saveToFileResults(
            Integer sampleNumber, Integer captureNumber, float depth, float diameter) {

        String filename = getFileName(sampleNumber, captureNumber, Filetype.MATRIX);
        File outFile = getOrCreateFile(filename);

        try (FileWriter writer = new FileWriter(outFile)) {
            StringBuilder str = new StringBuilder();
            str.append(depth);
            str.append(',');
            str.append(diameter);

            writer.write(str.toString());
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(LOG_TAG, "Successfully wrote the file " + filename);
        }
    }

    // Log intermediate data
    public void saveToFileLogInfo(Integer sampleNumber, Integer captureNumber, String logInfo) {
        String filename = PREFIX + sampleNumber + "_" + captureNumber + "_log" + ".txt";
        File outFile = getOrCreateFile(filename);
        try (FileWriter writer = new FileWriter(outFile)) {
            writer.write(logInfo);
            writer.flush();
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(LOG_TAG, "Successfully wrote the file " + filename);
        }
    }

    // save rgb_disp_plot
    public void saveToFileDispPlot(Integer sampleNumber, Integer captureNumber, byte[] rgb_disp_plot) {
        String filename = PREFIX + sampleNumber + "_" + captureNumber + "_rgb_disp_plot" + ".png";
        File outFile = getOrCreateFile(filename);
        try (FileOutputStream writer = new FileOutputStream(outFile)) {
            writer.write(rgb_disp_plot);
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(LOG_TAG, "Successfully wrote the file " + filename);
        }
    }

    // save center_depth_plot
    public void saveToFileCenterDepthPlot(Integer sampleNumber, Integer captureNumber, byte[] center_depth_plot) {
        String filename = PREFIX + sampleNumber + "_" + captureNumber + "_center_depth_plot" + ".png";
        File outFile = getOrCreateFile(filename);
        try (FileOutputStream writer = new FileOutputStream(outFile)) {
            writer.write(center_depth_plot);
        } catch (IOException e) {
            e.printStackTrace();
            Log.i(LOG_TAG, "Successfully wrote the file " + filename);
        }
    }

    public void saveToFileProjectionMatrix(
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


    public Integer[] getMaxSampleCaptureNums() {
        File dir = new File(filepath, FOLDER);
        if (!dir.exists()) {
            return new Integer[]{0, 1};
        }

        Integer maxS = 0;
        Integer maxC = 0;
        FilenameFilter filter = (d, name) -> !name.contains(".");
        for (File file : dir.listFiles(filter)) {
            String name = file.getName();
            String[] s = name.replaceAll(PREFIX, "").split("_");
            maxS = Math.max(Integer.parseInt(s[0]), maxS);
            maxC = Math.max(Integer.parseInt(s[1]), maxC);
        }
        return new Integer[] {maxS + 1, maxC + 1};

    }
    public void deleteFiles() {
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
