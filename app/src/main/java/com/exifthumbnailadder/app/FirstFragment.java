/*
 * Copyright (C) 2021 Fab Stz <fabstz-it@yahoo.fr>
 *
 * This file is part of Exif Thumbnail Adder. An android app that adds
 * thumbnails in EXIF tags of your pictures that don't have one yet.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.exifthumbnailadder.app;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.text.Html;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.exifinterface.media.ExifInterface;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceManager;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.UserPrincipal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class FirstFragment extends Fragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private final boolean enableLog = false;
    private final String TAG = "ETALog";
    //private final String THUMB_EXT = ".tmp-thumbAdder";
    private final String THUMB_EXT = "";

    SharedPreferences prefs = null;
    TextView textViewLog, textViewDirList;
    Handler mHandler;
    public final static SpannableStringBuilder log = new SpannableStringBuilder("");
    ScrollView scrollview = null;
    private boolean stopProcessing = false;
    private boolean isProcessing = false;

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState
    ) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false);
    }

    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        Context fragmentContext = (MainActivity) view.getContext();

        /*view.findViewById(R.id.button_first).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                NavHostFragment.findNavController(FirstFragment.this)
                        .navigate(R.id.action_FirstFragment_to_SecondFragment);
            }
        });*/
        view.findViewById(R.id.button_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showSettingsActivity(view );
            }
        });
        view.findViewById(R.id.button_addThumbs).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Button start = (Button) getView().findViewById(R.id.button_addThumbs);
                Button stop = (Button) getView().findViewById(R.id.button_stopProcess);

                start.setVisibility(Button.GONE);
                stop.setVisibility(Button.VISIBLE);
                addThumbsUsingTreeUris(view);
                //addThumbsUsingFiles(view);
            }
        });
        view.findViewById(R.id.button_stopProcess).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                stopProcessing = true;
                Button start = (Button) getView().findViewById(R.id.button_addThumbs);
                Button stop = (Button) getView().findViewById(R.id.button_stopProcess);
                start.setVisibility(Button.VISIBLE);
                stop.setVisibility(Button.GONE);
            }
        });

        textViewLog = (TextView)view.findViewById(R.id.textview_log);
        textViewDirList = (TextView)view.findViewById(R.id.textview_dir_list);
        scrollview = ((ScrollView)  view.findViewById(R.id.scrollview));
        updateTextViewDirList();

        LinearLayout ll = (LinearLayout)view.findViewById(R.id.block_allFilesAccess);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Use of "All Files Access Permissions" may result in rejection from the google play store
            // We use it only to be able to update the attributes of the files (ie timestamps)
            if (SettingsActivity.haveAllFilesAccessPermission())
                ll.setVisibility(View.GONE);
            else
                ll.setVisibility(View.VISIBLE);
        } else {
            ll.setVisibility(View.GONE);
        }
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if ( key.equals("srcUris")) {
            updateTextViewDirList();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        textViewLog.setText(log);
    }

    public static Bitmap createThumbnail(InputStream is) throws BadOriginalImageException {
        Bitmap original = null;
        original = BitmapFactory.decodeStream(is);

        if (original == null) {
            throw new BadOriginalImageException();
        }
        int imageWidth = original.getWidth();
        int imageHeight = original.getHeight();
        int thumbnailWidth = 160;
        int thumbnailHeight = 120;
        if (imageWidth < imageHeight) {
            // Swap thumbnail width and height to keep a relative aspect ratio
            int temp = thumbnailWidth;
            thumbnailWidth = thumbnailHeight;
            thumbnailHeight = temp;
        }
        if (imageWidth < thumbnailWidth) thumbnailWidth = imageWidth;
        if (imageHeight < thumbnailHeight) thumbnailHeight = imageHeight;

        // https://stackoverflow.com/a/13252754
        // Apply the principle of not reducing more than 50% each time
        int tmpWidth = imageWidth;
        int tmpHeight = imageHeight;
        Bitmap thumbnail = original;
        while (tmpWidth / thumbnailWidth > 2 || tmpHeight / thumbnailHeight > 2) {
            tmpWidth /= 2;
            tmpHeight /= 2;
            thumbnail = Bitmap.createScaledBitmap(thumbnail, tmpWidth, tmpHeight, true);
        }
        thumbnail = Bitmap.createScaledBitmap(thumbnail, thumbnailWidth, thumbnailHeight, true);

        return thumbnail;
    }

    private Bitmap rotateThumbnail(Bitmap tb_bitmap, int degrees) {
        // Google's "Files" app applies the rotation of the principal picture to the thumbnail
        // when it displays the thumbnail. Kde in PTP mode and Windows don't do that, so the have to
        // rotate the thumbnail.
        // Neither GoogleFiles, nor the others consider the "Orientation" tag when set on IFD1
        // (which is for the thumbnail), so it is not usefsull to set that orientation tag

        // Get rotation & rotate thumbnail
        Matrix matrix = new Matrix();
        matrix.postRotate(degrees);
        return Bitmap.createBitmap(tb_bitmap, 0, 0, tb_bitmap.getWidth(), tb_bitmap.getHeight(), matrix, true);
    }

    private Bitmap makeThumbnail(InputStream srcImgIs) throws BadOriginalImageException {
        Bitmap tb_bitmap = null;
        try {
            tb_bitmap = this.createThumbnail(srcImgIs);
            srcImgIs.close();
        } catch (BadOriginalImageException e) {
            throw e;
        } catch (Exception e) {
            // TODO
            e.printStackTrace();
        }

        if (tb_bitmap == null) {
            Log.e(TAG, "bitmap is null... Abnormal");
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        } else {
            return tb_bitmap;
        }
    }

    private Bitmap makeThumbnailRotated(File name, boolean rotateThumbnail, int degrees) throws Exception, BadOriginalImageException {
        Bitmap tb_bitmap = null;
        InputStream srcImgIs = null;
        try {
            srcImgIs = new FileInputStream(name);
            tb_bitmap = makeThumbnail(srcImgIs);
        } catch (BadOriginalImageException e) {
            throw e;
        } catch (FileNotFoundException e) {
            //TODO
            e.printStackTrace();
        }

        if (rotateThumbnail) {
            tb_bitmap = rotateThumbnail(tb_bitmap, degrees);
        }
        if (tb_bitmap != null) {
            return tb_bitmap;
        } else {
            throw new Exception("Empty tb_bitmap");
        }
    }

    public Bitmap makeThumbnailRotated(DocumentFile inputDf, boolean rotateThumbnail, int degrees) throws Exception, BadOriginalImageException {
        Bitmap tb_bitmap = null;
        InputStream is = null;
        try {
            is = getActivity().getContentResolver().openInputStream(inputDf.getUri());
            tb_bitmap = makeThumbnail(is);
        } catch (BadOriginalImageException e) {
            throw e;
        } catch (Exception e) {
            e.printStackTrace();
            //TODO
        }

        if (rotateThumbnail) {
            tb_bitmap = rotateThumbnail(tb_bitmap, degrees);
        }
        if (tb_bitmap != null) {
            return tb_bitmap;
        } else {
            throw new Exception("Empty tb_bitmap");
        }
    }

    private static class MyFilenameFilter implements FilenameFilter {

        private String acceptedPath;
        private boolean recurse;

        public MyFilenameFilter(String acceptedPath, boolean recurse) {
            this.acceptedPath = acceptedPath;
            this.recurse = recurse;
        }

        //apply a filter
        @Override
        public boolean accept(File dir, String name) {
            boolean result;
            //if (true) {
            String dirTrailing = dir.toString() + File.separator;
            boolean recurseCheckAccept = true;
            //if (!recurse && new File(dirTrailing + name).isDirectory()) {
            if (new File(dirTrailing + name).isDirectory()) {
                recurseCheckAccept = false;
            }
            //Log.i("MyLog Here", "D: " + dir.toString() + " N: " + name + " " + dirTrailing + " recurseCheckAccept? " + recurseCheckAccept);

            if (dirTrailing.equals(this.acceptedPath) && recurseCheckAccept) {
                result = true;
            } else {
                result = false;
            }
            return result;
        }
    }

    public void listDirectory(File dir, int level) {
        File[] firstLevelFiles = dir.listFiles();
        if (firstLevelFiles != null && firstLevelFiles.length > 0) {
            for (File aFile : firstLevelFiles) {
                for (int i = 0; i < level; i++) {
                    //System.out.print("\t");
                }
                if (aFile.isDirectory()) {
                    //System.out.println("[" + aFile.getName() + "]");
                    listDirectory(aFile, level + 1);
                } else {
                    //System.out.println(aFile.getName());
                }
            }
        }
    }

    private void listDirectoryAsFile(File dir, int level, ArrayList<File> arrayList, File excluded) {
        File[] firstLevelFiles = dir.listFiles();
        if (firstLevelFiles != null && firstLevelFiles.length > 0) {
            for (File aFile : firstLevelFiles) {
                if (!aFile.getPath().startsWith(excluded.getPath())) {
                    for (int i = 0; i < level; i++) {
                        //System.out.print("\t");
                    }
                    if (aFile.isDirectory()) {
                        //System.out.println("[" + aFile.getName() + "]");
                        listDirectoryAsFile(aFile, level + 1, arrayList, excluded);
                    } else {
                        //System.out.println(aFile.getName());
                        arrayList.add(aFile);
                    }
                }
            }
        }
    }

    private DocumentFile[] listDocFilesToProcess(Uri uri, int level, String excluded) {
        DocumentFile baseDf = DocumentFile.fromTreeUri(getActivity(), uri);
        boolean canRead = baseDf.canRead();

        ArrayList<DocumentFile> fileList = new ArrayList<DocumentFile>();
        listDocFilesToProcess_int(baseDf, 0, fileList, excluded);
        DocumentFile[] filesInDir= new DocumentFile[fileList.size()];
        filesInDir = fileList.toArray(filesInDir);
        return filesInDir;
    }

    private void listDocFilesToProcess_int(DocumentFile df, int level, ArrayList<DocumentFile> arrayList, String excluded) {
        DocumentFile[] firstLevelFiles = df.listFiles();
        if (firstLevelFiles != null && firstLevelFiles.length > 0) {
            for (DocumentFile aFile : firstLevelFiles) {
                for (int i = 0; i < level; i++) {
                    //System.out.print("\t");
                }
                if (aFile.isDirectory()) {
                    if (aFile.getName().equals(excluded)) {
                        if (enableLog) Log.i(TAG, getString(R.string.frag1_log_skipping_excluded_dir, excluded, aFile.getUri().getPath()));
                    } else {
                        //System.out.println("[" + aFile.getName() + "]");
                        listDocFilesToProcess_int(aFile, level + 1, arrayList, excluded);
                    }
                } else {
                    //System.out.println(aFile.getName());
                    arrayList.add(aFile);
                }
            }
        }
    }

    private String getVolumeRootPath(String srcPath) {
        String volumeRootPath = "";
        // get the path of the root for the volume on which the files/dir are located.
        // Ex: file/dir = /storage/1507-270B/DCIM.new/  --> volumeRootPath =  /storage/1507-270B
        // Ex: file/dir = /storage/emulated/0/DCIM      --> volumeRootPath =  /storage/emulated/0
        StorageManager myStorageManager = (StorageManager) getActivity().getSystemService(Context.STORAGE_SERVICE);
        StorageVolume mySV = myStorageManager.getStorageVolume(new File(srcPath));

        Class<?> storageVolumeClazz = null;
        try {
            storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
            Method getPath = storageVolumeClazz.getMethod("getPath");
            volumeRootPath = (String) getPath.invoke(mySV);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }

        if (enableLog) Log.i(TAG, "volumeRootPath: " + volumeRootPath);
        return volumeRootPath;

    }

    private String[] getVolumesDir() {
        ArrayList<String> volumesArrayList = new ArrayList<String>();
        String[] volumesArray = new String[volumesArrayList.size()];

        StorageManager myStorageManager = (StorageManager) getActivity().getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> mySV = myStorageManager.getStorageVolumes();
        Class<?> storageVolumeClazz = null;

        for (int i = 0; i < mySV.size(); i++) {
            try {
                storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
                Method getPath = storageVolumeClazz.getMethod("getPath");
                volumesArrayList.add((String) getPath.invoke(mySV.get(i)));
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            if (enableLog) Log.i(TAG, mySV.get(i).toString());

        }
        volumesArray = volumesArrayList.toArray(volumesArray);
        return volumesArray;
    }

    public static String getSecVolumeName(Context con, boolean normalize) {
        String volumeName = "";
        StorageManager myStorageManager = (StorageManager) con.getSystemService(Context.STORAGE_SERVICE);
        List<StorageVolume> mySVs = myStorageManager.getStorageVolumes();
        Class<?> storageVolumeClazz = null;

        for (StorageVolume mySV : mySVs) {
            try {
                if (! mySV.isPrimary()) {
                    storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
                    Method getUuid = storageVolumeClazz.getMethod("getUuid");
                    String mFsUuid = (String) getUuid.invoke(mySV);
                    if (normalize)
                        volumeName = normalizeUuid(mFsUuid);
                    else
                        volumeName = mFsUuid;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return volumeName;
    }


    /** {@hide} */
    public static @Nullable
    String normalizeUuid(@Nullable String fsUuid) {
        return fsUuid != null ? fsUuid.toLowerCase(Locale.US) : null;
    }

    public String getVolumeName(String srcPath) {
        // Inspired from
        // https://cs.android.com/android/platform/superproject/+/android-11.0.0_r1:frameworks/base/core/java/android/os/storage/StorageVolume.java;drc=1639e6b8eeaac34d44b1f1cd0d50a5c051852a65;l=321
        String volumeName = "";

        StorageManager myStorageManager = (StorageManager) getActivity().getSystemService(Context.STORAGE_SERVICE);
        StorageVolume mySV = myStorageManager.getStorageVolume(new File(srcPath));
        Class<?> storageVolumeClazz = null;

        if (mySV.isPrimary()) {
            volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY;
        } else {
            try {
                storageVolumeClazz = Class.forName("android.os.storage.StorageVolume");
                Method getUuid = storageVolumeClazz.getMethod("getUuid");
                String mFsUuid = (String)getUuid.invoke(mySV);
                volumeName = normalizeUuid(mFsUuid);
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }
        }
        return volumeName;
    }

    public static boolean isImageFile(String path) {
        // https://stackoverflow.com/a/30696106
        String mimeType = URLConnection.guessContentTypeFromName(path);
        return mimeType != null && mimeType.startsWith("image");
    }

    public static boolean isJpegImageFile(String path) {
        String mimeType = URLConnection.guessContentTypeFromName(path);
        return mimeType != null && mimeType.equals("image/jpeg");
    }

    private void copyFileAttributes(Path inFilePath, Path outFilePath) throws Exception {
        if (enableLog) Log.i(TAG, getString(R.string.frag1_log_copying_attr));
        try {
            BasicFileAttributes inAttrs = Files.readAttributes(inFilePath, BasicFileAttributes.class);

            // Copy owner attribute
            UserPrincipal user = Files.getOwner(inFilePath, LinkOption.NOFOLLOW_LINKS);
            Files.setOwner(outFilePath, user);

            // Copy time attributes
            Files.getFileAttributeView(outFilePath, BasicFileAttributeView.class).setTimes(inAttrs.lastModifiedTime(), inAttrs.lastAccessTime(), inAttrs.creationTime());

            // Copy Posix attributes
            Set<PosixFilePermission> inPosix = Files.getPosixFilePermissions(inFilePath, LinkOption.NOFOLLOW_LINKS);
            Files.setPosixFilePermissions(outFilePath, inPosix);

        } catch (Exception e) {
            e.printStackTrace();
            throw new CopyAttributesFailedException(e);
        }
    }

    private void copyFileAttributes(Uri sourceUri, Uri targetUri) throws Exception {
        /*
           We can't do it through SAF.
           But we can switch to "filesystem" mode

           It seems it is possible to update the attributes even on secondary external storage
           (like sdcard) although I thought we could only write on external_primary, and we could
           not write on secondary volumes (ie. sdcard)
         */

        if (targetUri == null)
            return;

        // Don't do anything if volume root id is not "primary"
        // NOT NEEDED WITH ANDROID 10 BECAUSE IT WORKS ALSO WITH 2ND EXT STORAGE
        //if (!FileUtil.getVolumeIdFromTreeUri(sourceUri).equals("primary"))
        //    return;

        Path inFilePath = null;
        Path outFilePath = null;

        if (sourceUri.getScheme().equals("file")) {
            inFilePath = Paths.get(sourceUri.getPath());
        } else {
            inFilePath = Paths.get(FileUtil.getFullDocIdPathFromTreeUri(sourceUri, getContext()));
        }

        if (targetUri.getScheme().equals("file")) {
            outFilePath = Paths.get(targetUri.getPath());
        } else {
            outFilePath = Paths.get(FileUtil.getFullDocIdPathFromTreeUri(targetUri, getContext()));
        }

        copyFileAttributes(inFilePath, outFilePath);
    }

    public void addThumbsUsingFiles(View view) {
        isProcessing = true;
        stopProcessing = false;
        log.clear();
        updateUiLog(getString(R.string.frag1_log_starting));

        String[] volumesDir = getVolumesDir();
        String[] mainDirs = {"DCIM", "Pictures"};

        for (int j = 0; j < volumesDir.length; j++) {
            for (int d = 0; d < mainDirs.length; d++) {
                String srcPath = volumesDir[j] + "/" + mainDirs[d] + "/";
                String volumeName = getVolumeName(srcPath);
                String secStorageVolName = getSecVolumeName(getContext(), true);
                String secStorageDirName = prefs.getString("excluded_sec_vol_prefix", getString(R.string.pref_excludedSecVolPrefix_defaultValue)) + secStorageVolName;
                String excludedPath = volumesDir[j] + "/" + mainDirs[d] + "/" + secStorageDirName + "/";

                updateUiLog(Html.fromHtml("<br><u><b>"+getString(R.string.frag1_log_processing_dir, srcPath) + "</b></u><br>",1));

                // Get mounted path of the volume holding this folder
                String volumeRootPath = getVolumeRootPath(srcPath);

                // We can only write to primary external, so set writablePath accordingly
                String writablePath;
                if (volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY) {
                    writablePath = getVolumeRootPath(srcPath);
                } else {
                    // Attention "getExternalStorageDirectory" deprecated from API 29,
                    writablePath = Environment.getExternalStorageDirectory().toString();
                }

                // https://stackoverflow.com/a/27996686
                //File[] filesInDir = new File(srcPath).listFiles(new MyFilenameFilter(srcPath, true));
                //File[] filesInDir = listFilesRecursive(new File(srcPath));
                ArrayList<File> fileList = new ArrayList<File>();
                listDirectoryAsFile(new File(srcPath), 0, fileList, new File(excludedPath));
                File[] filesInDir = new File[fileList.size()];
                filesInDir = fileList.toArray(filesInDir);
                //if (enableLog) Log.i(TAG, "Array of filesInDir: " + filesInDir.toString());

                for (int i = 0; i < filesInDir.length; i++) {
                    if (enableLog) Log.i(TAG, getString(R.string.frag1_log_processing_path_filename, filesInDir[i].getPath(), filesInDir[i].getName()));

                    // Prepare dir variables
                    String mainDir = ""; // if "mountDir/DCIM/dir1/s2/file.jpg" --> DCIM
                    String subDir = ""; // if "mountDir/DCIM/dir1/s2/file.jpg" --> dir1/s2/file.jpg

                    if (filesInDir[i].toString().startsWith(volumeRootPath)) {
                        String tmp = filesInDir[i].toString().substring(volumeRootPath.length()); // "/DCIM/dir1/s2/file.jpg"
                        String[] b = tmp.split(File.separator); // [0]: "" ; [1]: "DCIM; [2]: "dir1"...
                        mainDir = b[1]; // "DCIM"
                        subDir = String.join(File.separator, Arrays.copyOfRange(b, 2, b.length - 1)); // "dir1/s2"
                    }
                    if (enableLog) Log.i(TAG, "mainDir: " + mainDir);
                    if (enableLog) Log.i(TAG, "subDir: " + subDir);

                    updateUiLog("⋅ [" + (i+1) + "/" + filesInDir.length + "] " +
                            subDir + (subDir.isEmpty() ? "" : File.separator) +
                            filesInDir[i].getName() + "... ");

                    if (!isJpegImageFile(filesInDir[i].toString())) {
                        if (enableLog) Log.i(TAG, getString(R.string.frag1_log_skipping_path_filename, filesInDir[i].getPath() , filesInDir[i].getName()));
                        updateUiLog(getString(R.string.frag1_log_skipping_not_jpeg));
                        continue;
                    }

                    if(filesInDir[i].length() == 0) {
                        updateUiLog(getString(R.string.frag1_log_skipping_empty_file));
                        continue;
                    }

                    // a. check if sourceFile already has Exif Thumbnail
                    ExifInterface srcImgExifInterface = null;
                    InputStream srcImgIs = null;
                    ByteArrayOutputStream newImgOs = new ByteArrayOutputStream();

                    boolean srcImgHasThumbnail = false;
                    int srcImgDegrees = 0;

                    try {
                        srcImgIs = new FileInputStream(filesInDir[i]);
                        srcImgExifInterface = new ExifInterface(srcImgIs);
                        if (srcImgExifInterface != null) {
                            srcImgHasThumbnail = srcImgExifInterface.hasThumbnail();
                            srcImgDegrees = srcImgExifInterface.getRotationDegrees();
                        }
                        srcImgIs.close();
                        srcImgExifInterface = null;

                        if (srcImgHasThumbnail) {
                            updateUiLog(getString(R.string.frag1_log_skipping_has_thumbnail));
                            continue;
                        }
                    } catch (Exception e) {
                        updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_skipping_error, e.getMessage()) + "</span><br>", 1));
                        e.printStackTrace();
                        continue;
                    }

                    // a. extract thumbnail & write to output stream
                    try {
                        //if (enableLog) Log.i(TAG, "Creating thumbnail");
                        Bitmap thumbnail = makeThumbnailRotated(
                                filesInDir[i],
                                prefs.getBoolean("rotateThumbnails", true),
                                srcImgDegrees);

                        srcImgIs = new FileInputStream(filesInDir[i]);

                        switch (prefs.getString("exif_library", "exiflib_android-exif-extended")) {
                            case "exiflib_android-exif-extended":
                                writeThumbnailWithAndroidExifExtended(srcImgIs, newImgOs, Uri.fromFile(filesInDir[i]), thumbnail);
                                break;
                            case "exiflib_pixymeta":
                                if (!PixymetaInterface.hasPixymetaLib()) {
                                    updateUiLog(Html.fromHtml("<br><br><span style='color:red'>" + getString(R.string.frag1_log_pixymeta_missing) + "</span><br>", 1));
                                    return;
                                }
                                PixymetaInterface.writeThumbnailWithPixymeta(srcImgIs, newImgOs, thumbnail);
                                break;
                        }

                        // Close Streams
                        srcImgIs.close();
                        newImgOs.close();
                    } catch (BadOriginalImageException e) {
                        updateUiLog(getString(R.string.frag1_log_skipping_bad_image));
                        e.printStackTrace();
                        continue;
                    } catch (Exception e) {
                        updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_skipping_error, e.getMessage()) + "</span><br>", 1));
                        e.printStackTrace();
                        continue;
                    }

                    // Create folders where files are written
                    PathUtil pathUtil = new PathUtil(
                            writablePath,
                            mainDir,
                            subDir,
                            volumeName,
                            secStorageDirName,
                            prefs);
                    String tmpPath = pathUtil.getTmpDir(getActivity(), true);
                    String backupPath = pathUtil.getBackupDir(true);
                    String outputPath = pathUtil.getDestDir();

                    PathUtil.createDirFor(tmpPath);
                    PathUtil.createDirFor(backupPath);
                    PathUtil.createDirFor(outputPath);

                    String outputFilename = tmpPath + "/" + filesInDir[i].getName() + THUMB_EXT;

                    try {
                        // Write output file to disk
                        if (enableLog) Log.i(TAG, "Write to: " + outputFilename);

                        FileOutputStream outputStream;
                        outputStream = new FileOutputStream(outputFilename);
                        outputStream.write(newImgOs.toByteArray());
                        outputStream.close();
                        if (enableLog) Log.i(TAG, "Write to DONE: " + outputFilename);
                    } catch (FileNotFoundException e) {
                        updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_skipping_error, e.getMessage()) + "</span><br>", 1));
                        e.printStackTrace();
                    } catch (IOException e) {
                        updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_skipping_error, e.getMessage()) + "</span><br>", 1));
                        e.printStackTrace();
                    }

                    // Copy file attributes from source to target
                    try {
                        copyFileAttributes(filesInDir[i].toPath(), new File(outputFilename).toPath());
                    } catch (Exception e) {
                        updateUiLog(Html.fromHtml("<span style='color:#FFA500'>" + getString(R.string.frag1_log_could_not_copy_timestamp_and_attr, e.getMessage()) + "</span><br>", 1));
                        e.printStackTrace();
                    }

                    Path from, to = null;

                    // a. Move or copy original files (from DCIM) to backup dir (DCIM.bak)
                    if (prefs.getBoolean("backupOriginalPic", true)) {
                        from = filesInDir[i].toPath();
                        to = new File(backupPath + "/" + filesInDir[i].getName()).toPath();

                        if (!prefs.getBoolean("writeThumbnailedToOriginalFolder", false)) {
                            try {
                                Files.copy(from, to, REPLACE_EXISTING, COPY_ATTRIBUTES);
                            } catch (Exception e) {
                                updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_error_copying_doc, e.getMessage()) + "</span><br>", 1));
                                e.printStackTrace();
                                continue;
                            }
                        } else {
                            if (volumeName == MediaStore.VOLUME_EXTERNAL_PRIMARY) {
                                try {
                                    Files.move(from, to, ATOMIC_MOVE);
                                } catch (Exception e) {
                                    updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_error_copying_doc, e.getMessage()) + "</span><br>", 1));
                                    e.printStackTrace();
                                    continue;
                                }
                            } else {
                                // Do nothing
                            }
                        }
                    }

                    // a. Move new file (having Thumbnail) from DCIM.tmp folder to its final folder
                    //   DCIM (when in production), in this case we don't replace the file in DCIM
                    //   DCIM.new (in test), in this case we replace the file in DCIM.new
                    from = new File(outputFilename).toPath();
                    to = new File(outputPath + "/" + filesInDir[i].getName()).toPath();
                    try {
                        Files.move(from, to, REPLACE_EXISTING, ATOMIC_MOVE);
                    } catch (Exception e) {
                        updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_error_moving_doc, e.getMessage()) + "</span><br>", 1));
                        e.printStackTrace();
                        continue;
                    }

                    updateUiLog(Html.fromHtml("<span style='color:green'>"+getString(R.string.frag1_log_done)+"</span><br>",1));
                }
            }
        }
        updateUiLog(getString(R.string.frag1_log_finished));

        setIsProcessFalse(view);
    }

    public void addThumbsUsingTreeUris(View view) {
        isProcessing = true;
        stopProcessing = false;

        mHandler = new Handler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                log.clear();
                updateUiLog(getString(R.string.frag1_log_starting));

                {
                    updateUiLog(Html.fromHtml(getString(R.string.frag1_log_checking_workingdir_perm), 1));
                    if (!isWorkingDirPermOk()) {
                        updateUiLog(Html.fromHtml("<span style='color:red'>"+getString(R.string.frag1_log_ko)+"</span><br>", 1));
                        setIsProcessFalse(view);
                        stopProcessing = false;
                        return;
                    }
                    updateUiLog(Html.fromHtml("<span style='color:green'>"+getString(R.string.frag1_log_ok)+"</span><br>", 1));
                }

                String secVolName = getSecVolumeName(getActivity(), true);
                String secVolDirName = prefs.getString("excluded_sec_vol_prefix", getString(R.string.pref_excludedSecVolPrefix_defaultValue))+secVolName;

                InputDirs inputDirs = new InputDirs(prefs.getString("srcUris", ""));
                Uri[] treeUris = inputDirs.toUriArray();

                List<UriPermission> persUriPermList = getActivity().getContentResolver().getPersistedUriPermissions();

                // Iterate on folders containing source images
                for (int j = 0; j < treeUris.length; j++) {
                    updateUiLog(Html.fromHtml("<br><u><b>"+getString(R.string.frag1_log_processing_dir, FileUtil.getFullPathFromTreeUri(treeUris[j], getContext())) + "</b></u><br>",1));

                    {
                        // Check permission... If we don't have permission, continue to next volumeDir
                        updateUiLog(Html.fromHtml(getString(R.string.frag1_log_checking_perm), 1));
                        boolean perm_ok = false;
                        String tString = treeUris[j].toString();
                        for (UriPermission perm : persUriPermList) {
                            if (tString.startsWith(perm.getUri().toString())) {
                                if (perm.isReadPermission() && perm.isWritePermission()) {
                                    perm_ok = true;
                                    break;
                                }
                            }
                        }
                        if (!perm_ok) {
                            updateUiLog(Html.fromHtml("<span style='color:red'>"+getString(R.string.frag1_log_not_granted)+"</span><br>", 1));
                            continue;
                        }
                        updateUiLog(Html.fromHtml("<span style='color:green'>"+getString(R.string.frag1_log_ok)+"</span><br>", 1));

                    }

                    // 1. build list of files to process
                    DocumentFile[] docFilesToProcess = listDocFilesToProcess(treeUris[j], 0, secVolDirName);
                    updateUiLog(Html.fromHtml(getString(R.string.frag1_log_count_files_to_process, docFilesToProcess.length ) + "<br>",1));

                    // 1. Iterate on all files
                    for (int i = 0; i < docFilesToProcess.length; i++) {
                        if (stopProcessing) {
                            setIsProcessFalse(view);
                            stopProcessing = false;
                            updateUiLog(Html.fromHtml("<br><br>"+getString(R.string.frag1_log_stopped_by_user),1));
                            return;
                        }

                        String mainDir = UriUtil.getDD1(docFilesToProcess[i].getUri());
                        String subDir = UriUtil.getDDSubParent(docFilesToProcess[i].getUri());
                        String treeId = UriUtil.getTreeId(docFilesToProcess[i].getUri());
                        String docIdParent = UriUtil.getDParent(docFilesToProcess[i].getUri());
                        String subPath = docIdParent.replace(treeId, "");
                        subPath = subPath.startsWith("/") ? subPath.replaceFirst("/", "") : subPath;

                        updateUiLog("⋅ [" + (i+1) + "/" + docFilesToProcess.length + "] " +
                                subPath + (subPath.isEmpty() ? "" : File.separator) +
                                docFilesToProcess[i].getName() + "... ");

                        if (! docFilesToProcess[i].getType().equals("image/jpeg")) {
                            updateUiLog(getString(R.string.frag1_log_skipping_not_jpeg));
                            continue;
                        }

                        if (docFilesToProcess[i].length() == 0) {
                            updateUiLog(getString(R.string.frag1_log_skipping_empty_file));
                            continue;
                        }

                        // a. check if sourceFile already has Exif Thumbnail
                        ExifInterface srcImgExifInterface = null;
                        InputStream srcImgIs = null;
                        ByteArrayOutputStream newImgOs = new ByteArrayOutputStream();

                        boolean srcImgHasThumbnail = false;
                        int srcImgDegrees = 0;

                        try {
                            srcImgIs = getActivity().getContentResolver().openInputStream(docFilesToProcess[i].getUri());
                            srcImgExifInterface = new ExifInterface(srcImgIs);
                            if (srcImgExifInterface != null) {
                                srcImgHasThumbnail = srcImgExifInterface.hasThumbnail();
                                srcImgDegrees = srcImgExifInterface.getRotationDegrees();
                            }
                            srcImgIs.close();
                            srcImgExifInterface = null;

                            if (srcImgHasThumbnail) {
                                updateUiLog(getString(R.string.frag1_log_skipping_has_thumbnail));
                                continue;
                            }
                        } catch (Exception e) {
                            updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_skipping_error, e.getMessage()) + "</span><br>", 1));
                            e.printStackTrace();
                            continue;
                        }

                        Bitmap thumbnail;
                        // a. extract thumbnail & write to output stream
                        try {
                            //if (enableLog) Log.i(TAG, "Creating thumbnail");
                            thumbnail = makeThumbnailRotated(
                                    docFilesToProcess[i],
                                    prefs.getBoolean("rotateThumbnails", true),
                                    srcImgDegrees);

                            srcImgIs = getActivity().getContentResolver().openInputStream(docFilesToProcess[i].getUri());

                            switch (prefs.getString("exif_library", "exiflib_android-exif-extended")) {
                                case "exiflib_android-exif-extended":
                                    writeThumbnailWithAndroidExifExtended(srcImgIs, newImgOs, docFilesToProcess[i].getUri(), thumbnail);
                                    break;
                                case "exiflib_pixymeta":
                                    if (!PixymetaInterface.hasPixymetaLib()) {
                                        updateUiLog(Html.fromHtml("<br><br><span style='color:red'>" + getString(R.string.frag1_log_pixymeta_missing) + "</span><br>", 1));
                                        return;
                                    }
                                    PixymetaInterface.writeThumbnailWithPixymeta(srcImgIs, newImgOs, thumbnail);
                                    break;
                            }

                            // Close Streams
                            srcImgIs.close();
                            newImgOs.close();
                        } catch (BadOriginalImageException e) {
                            updateUiLog(getString(R.string.frag1_log_skipping_bad_image));
                            e.printStackTrace();
                            continue;
                        } catch (Exception e) {
                            updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_skipping_error, e.getMessage()) + "</span><br>", 1));
                            e.printStackTrace();
                            continue;
                        }

                        // a. create output dirs
                        PathUtil pathUtil = new PathUtil(
                                docFilesToProcess[i].getUri(),
                                mainDir,
                                subDir,
                                UriUtil.getTVolId(treeUris[j]),
                                secVolDirName,
                                prefs);

                        Uri tmpUri = pathUtil.getTmpUri(getContext(), false);
                        Uri backupUri = pathUtil.getBackupUri(getContext(), false);
                        Uri outputUri = pathUtil.getDestUri(getContext());

                        PathUtil.createDirFor(getContext(), tmpUri);
                        PathUtil.createDirFor(getContext(), backupUri);
                        PathUtil.createDirFor(getContext(), outputUri);

                        // a. write outputstream to disk
                        Uri outputTmpFileUri = null;
                        try  {
                            String filename = docFilesToProcess[i].getName() + THUMB_EXT;
                            outputTmpFileUri = getOutputFileUri(tmpUri, filename);
                            OutputStream outputStream = getOutputStreamForTreeUri(outputTmpFileUri);

                            //if (enableLog) Log.i(TAG, "Write to: " + tmpUri.getPath() + File.separator + filename);
                            outputStream.write(newImgOs.toByteArray());
                            outputStream.close();
                            //if (enableLog) Log.i(TAG, "Write to DONE");
                        } catch (Exception e) {
                            updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_skipping_error, e.getMessage()) + "</span><br>", 1));
                            e.printStackTrace();
                            continue;
                        }

                        if (thumbnail != null) {
                            if (prefs.getString("exif_library", "exiflib_android-exif-extended").equals("exiflib_libexif")) {
                                try {
                                    String outFilepath;
                                    if ( outputTmpFileUri.getScheme().equals("file")) {
                                        outFilepath = outputTmpFileUri.getPath();
                                    } else {
                                        outFilepath = FileUtil.getFullDocIdPathFromTreeUri(outputTmpFileUri, getContext());
                                    }

                                    new NativeLibHelper().writeThumbnailWithLibexifThroughFile(
                                            FileUtil.getFullDocIdPathFromTreeUri(docFilesToProcess[i].getUri(), getContext()),
                                            outFilepath,
                                            thumbnail);
                                } catch (Exception e) {
                                    updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_skipping_error, e.getMessage()) + "</span><br>", 1));
                                    e.printStackTrace();
                                    continue;
                                }
                            }
                        }

                        // a. Copy attributes from original file to tmp file
                        try {
                            copyFileAttributes(docFilesToProcess[i].getUri(), outputTmpFileUri);
                        } catch (CopyAttributesFailedException e) {
                            updateUiLog(Html.fromHtml("<span style='color:#FFA500'>" + getString(R.string.frag1_log_could_not_copy_timestamp_and_attr, e.getMessage()) + "</span><br>", 1));
                            e.printStackTrace();
                        } catch (Exception e) {
                            updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_error, e.getMessage()) + "</span><br>", 1));
                            e.printStackTrace();
                            continue;
                        }

                        Uri outputFile = null;
                        Uri sourceFile = null;
                        Uri originalImage = null;
                        Uri targetDir = null;

                        // a. Move or copy original files (from DCIM) to backup dir (DCIM.bak)
                        if (prefs.getBoolean("backupOriginalPic", true)) {
                            sourceFile = docFilesToProcess[i].getUri();
                            originalImage = sourceFile;
                            targetDir = backupUri;

                            if (prefs.getBoolean("writeThumbnailedToOriginalFolder", false)) {
                                // We do a move (so that the file with a thumbnail can be placed to the original dir)
                                try {
                                    originalImage = moveDocument(sourceFile, UriUtil.buildDParentAsUri(sourceFile), targetDir, false);
                                } catch (DestinationFileExistsException e) {
                                    updateUiLog(Html.fromHtml("<span style='color:red'>"+getString(R.string.frag1_log_cannot_move_to_backup)+"</span><br>",1));
                                    e.printStackTrace();
                                    continue;
                                } catch (CopyAttributesFailedException e) {
                                    updateUiLog(Html.fromHtml("<span style='color:#FFA500'>" + getString(R.string.frag1_log_could_not_copy_timestamp_and_attr, e.getMessage()) + "</span><br>", 1));
                                    e.printStackTrace();
                                } catch (Exception e) {
                                    updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_error_moving_doc, e.getMessage()) + "</span><br>", 1));
                                    e.printStackTrace();
                                    continue;
                                }
                            } else {
                                // We do a copy
                                try {
                                    copyDocument(sourceFile, targetDir, true,
                                            prefs.getBoolean("keepTimeStampOnBackup", true));
                                } catch (CopyAttributesFailedException e) {
                                    updateUiLog(Html.fromHtml("<span style='color:#FFA500'>" + getString(R.string.frag1_log_could_not_copy_timestamp_and_attr, e.getMessage()) + "</span><br>", 1));
                                    e.printStackTrace();
                                } catch (Exception e) {
                                    updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_error_copying_doc, e.getMessage()) + "</span><br>", 1));
                                    e.printStackTrace();
                                    continue;
                                }
                            }
                        }

                        // a. Move new file (having Thumbnail) from tmp folder to its final folder
                        // final folder depends on the setting: "writeThumbnailedToOriginalFolder"
                        sourceFile = outputTmpFileUri;
                        targetDir = outputUri;

                        boolean replaceExising = false;
                        if ( prefs.getBoolean("overwriteDestPic", false)) {
                            replaceExising = true;
                        }

                        try {
                            outputFile = moveDocument(sourceFile, tmpUri, targetDir, replaceExising);
                        } catch (DestinationFileExistsException e) {
                            updateUiLog(Html.fromHtml("<span style='color:red'>"+ getString(R.string.frag1_log_overwrite_not_allowed)+"</span><br>",1));
                            e.printStackTrace();
                            continue;
                        } catch (CopyAttributesFailedException e) {
                            updateUiLog(Html.fromHtml("<span style='color:#FFA500'>" + getString(R.string.frag1_log_could_not_copy_timestamp_and_attr, e.getMessage()) + "</span><br>", 1));
                            e.printStackTrace();
                        } catch (Exception e) {
                            updateUiLog(Html.fromHtml("<span style='color:red'>" + getString(R.string.frag1_log_error_moving_doc, e.getMessage()) + "</span><br>", 1));
                            e.printStackTrace();
                            continue;
                        }

                        updateUiLog(Html.fromHtml("<span style='color:green'>" + getString(R.string.frag1_log_done) + "</span><br>",1));

                        //Update the value background thread to UI thread
                        mHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                //textViewLog.setText(log);
                            }
                        });
                    }
                }
                updateUiLog(getString(R.string.frag1_log_finished));

                setIsProcessFalse(view);
            }
        }).start();
    }

    public Uri getOutputFileUri(Uri tmpUri, String filename) {
        if (tmpUri.getScheme().equals("file")) {
            return Uri.withAppendedPath(tmpUri, filename);
        }

        Uri outputFileUri = null;
        DocumentFile outputFileDf = DocumentFile.fromTreeUri(getContext(), tmpUri).findFile(filename);
        try {
            if (outputFileDf != null) {
                outputFileUri = outputFileDf.getUri();
            } else {
                outputFileUri = DocumentsContract.createDocument(
                        getActivity().getContentResolver(),
                        tmpUri,
                        "image/jpg",
                        filename);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return outputFileUri;
    }

    public OutputStream getOutputStreamForTreeUri (Uri outputFileUri) {
        OutputStream outputStream = null;

        try {
            outputStream = getActivity().getContentResolver().openOutputStream(outputFileUri);
        } catch (Exception e ){
            e.printStackTrace();
        }

        //if (enableLog) Log.i(TAG, "OutputStream ready for: " + outputFileUri.getPath());
        return outputStream;
    }

    private void copyDocument(Uri sourceUri, Uri targetParentUri, boolean replaceExisting, boolean copyFileAttributes)
        throws Exception {
        // INFO : copy looses timestamp, so we copyAttributes at the end.
        String displayName, targetParentPath;

        if (sourceUri.getScheme().equals("file")) {
            displayName = sourceUri.getLastPathSegment();
        } else {
            displayName = UriUtil.getDName(sourceUri);
        }

        targetParentPath = UriUtil.getDocId(targetParentUri);

        Uri targetUri = DocumentsContract.buildDocumentUriUsingTree(
                targetParentUri,
                targetParentPath + File.separator + displayName );
        boolean targetExists = DocumentFile.fromTreeUri(getContext(), targetUri).exists();

        Uri targetTmpUri = DocumentsContract.buildDocumentUriUsingTree(
                targetParentUri,
                targetParentPath + File.separator + displayName + "_tmp" );
        boolean targetTmpExists = DocumentFile.fromTreeUri(getContext(), targetTmpUri).exists();

        if (targetExists && !replaceExisting) {
            if (enableLog) Log.i(TAG, getString(R.string.frag1_log_file_exists, targetUri.toString()));
            return;
        }

        if (!targetTmpExists) {
            try {
                //targetUri = DocumentsContract.createDocument(getActivity().getContentResolver(), targetParentUri, "image/jpeg", displayName);
                // We don't use the correct filename but another one in the hope that this will
                // avoid indexing the file while its attributes are not fully copied. hence we don't set mimeType and use
                // a temporary extension
                targetTmpUri = DocumentsContract.createDocument(getActivity().getContentResolver(), targetParentUri, "", displayName + "_tmp");
            } catch (Exception e) {
                throw e;
                //e.printStackTrace();
            }
        }

        copyDocument(sourceUri, targetParentUri, targetTmpUri);

        try {
            // Copy attributes of source file to the target file
            if (copyFileAttributes)
                copyFileAttributes(sourceUri, targetTmpUri);
        } catch (CopyAttributesFailedException e) {
            throw e;
        } finally {
            // We don't use the correct filename but another one in the hope that this will
            // avoid indexing the file while its attributes are not fully copied. Hence, here we rename
            // the file to its correct name.
            if (targetExists) {
                try {
                    DocumentsContract.deleteDocument(getActivity().getContentResolver(), targetUri);
                } catch (Exception e) {
                    throw e;
                    //e.printStackTrace();
                }
            }
            DocumentsContract.renameDocument(getActivity().getContentResolver(), targetTmpUri, displayName);
        }
    }

    private void copyDocument(Uri sourceUri, Uri targetParentUri, Uri targetUri) throws Exception {
        // Copy is most time not possible. See:
        // https://stackoverflow.com/questions/66660155/android-saf-cannot-copy-file-flag-supports-copy-not-set
        // So we check if it is supported, otherwise, we fall back to copying with streams.

        if (UriUtil.supportsCopy(getContext(), sourceUri)) {
            // Delete destnation file
            // TODO I don't know if this is actually necessary. We'll see if the case happens

            if (enableLog) Log.i(TAG, "File supports DocumentsContract.copyDocument... Copying...");
            try {
                Uri outUri = DocumentsContract.copyDocument(getActivity().getContentResolver(), sourceUri, targetParentUri);
            } catch (Exception e ) {
                throw e;
                //e.printStackTrace();
            }
        } else {
            copyDocumentWithStream(sourceUri, targetUri);
        }
    }

    private void copyDocumentWithStream(Uri sourceUri, Uri targetUri) throws Exception {
        try {
            InputStream is = getActivity().getContentResolver().openInputStream(sourceUri);
            OutputStream os = getActivity().getContentResolver().openOutputStream(targetUri);

            byte[] buf = new byte[8192];
            int length;
            while ((length = is.read(buf)) > 0) {
                os.write(buf, 0, length);
            }
            os.close();
            is.close();
        } catch (Exception e) {
            throw e;
            //e.printStackTrace();
        }
    }

    private Uri moveDocument(Uri sourceUri, Uri sourceParentUri, Uri targetParentUri, boolean replaceExisting)
            throws DestinationFileExistsException, Exception {
        // INFO : move keeps timestamp
        String displayName, targetParentPath;

        if (sourceUri.getScheme().equals("file")) {
            displayName = sourceUri.getLastPathSegment();
        } else {
            displayName = UriUtil.getDName(sourceUri);
        }

        targetParentPath = UriUtil.getDocId(targetParentUri);

        Uri targetUri = DocumentsContract.buildDocumentUriUsingTree(
                targetParentUri,
                targetParentPath + File.separator + displayName);

        boolean targetExists = DocumentFile.fromTreeUri(getContext(), targetUri).exists();

        if (targetExists && !replaceExisting) {
            if (enableLog) Log.i(TAG, getString(R.string.frag1_log_file_exists, targetUri.toString()));
            throw new DestinationFileExistsException();
        }

        try {

            if (targetExists) {
                DocumentsContract.deleteDocument(getActivity().getContentResolver(), targetUri);
            }

            if (sourceUri.getScheme().equals("file")) {
                copyDocument(sourceUri, targetParentUri,true, true);
                new File(sourceUri.getPath()).delete();
                return targetUri;
            } else {
                return DocumentsContract.moveDocument(getActivity().getContentResolver(), sourceUri, sourceParentUri, targetParentUri);
            }

        } catch (Exception e) {
            throw e;
            //e.printStackTrace();
        }
    }

    private void writeThumbnailWithAndroidExifExtended (
            InputStream srcImgIs, OutputStream newImgOs, Uri inputUri, Bitmap thumbnail)
            throws Exception {
        try {
            // Andoid-Exif-Extended will write twice the APP1 structure to the file,
            // but it seems to copy perfectly

            // It was necessary to add the method writeExif(InputStream, OutputStream)
            // see https://github.com/sephiroth74/Android-Exif-Extended/pull/24

            it.sephiroth.android.library.exif2.ExifInterface sInExif = new it.sephiroth.android.library.exif2.ExifInterface();

            sInExif.readExif(srcImgIs, it.sephiroth.android.library.exif2.ExifInterface.Options.OPTION_ALL );
            sInExif.setCompressedThumbnail(thumbnail);

            // Close & Reopen InputStream, otherwise writeExif will fail with an exception
            // because srcImgIs was already read
            srcImgIs.close();
            srcImgIs = getActivity().getContentResolver().openInputStream(inputUri);

            // writeExif recopies anyway the tags that are in srcImgIs (which will be added
            // to those already in sInExif). It is necessary to call readExif,
            // otherwise addition of tags will crash (internal_writer needs a base coming
            // from another file, ie. for the SOS tag
            sInExif.writeExif(srcImgIs, newImgOs);
        } catch (Exception e) {
            throw e;
        }
    }

    public void showSettingsActivity(View view) {
        Intent intent = new Intent(getContext(), SettingsActivity.class);
        startActivity(intent);
    }

    public boolean isWorkingDirPermOk() {
        List<UriPermission> persUriPermList = getActivity().getContentResolver().getPersistedUriPermissions();
        Uri uri = WorkingDirPermActivity.workingDirPermMissing(prefs, persUriPermList, getContext());
        if (uri == null) {
            return true;
        } else {
            Intent intent = new Intent(getContext(), WorkingDirPermActivity.class);
            startActivity(intent);
            return false;
        }
    }

    public void updateTextViewDirList() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        InputDirs inputDirs = new InputDirs(prefs.getString("srcUris", ""));
        if (inputDirs.size() == 0) {
            textViewDirList.setText(R.string.frag1_text_no_dir_selected);
        } else {
            textViewDirList.setText(inputDirs.toStringForDisplay(getContext()));
        }
    }

    public void updateUiLog(String text) {
        if (enableLog) Log.i(TAG, text);
        log.append(text);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewLog.setText(log);
                // Stuff that updates the UI
                scrollview.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollview.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        });

    }
    public void updateUiLog(Spanned text) {
        if (enableLog) Log.i(TAG, text.toString());
        log.append(text);
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                textViewLog.setText(log);
                // Stuff that updates the UI
                scrollview.post(new Runnable() {
                    @Override
                    public void run() {
                        scrollview.fullScroll(ScrollView.FOCUS_DOWN);
                    }
                });
            }
        });
    }

    public void setIsProcessFalse(View view) {
        isProcessing = false;
        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Button start = (Button)getView().findViewById(R.id.button_addThumbs);
                Button stop =  (Button)getView().findViewById(R.id.button_stopProcess);
                start.setVisibility(Button.VISIBLE);
                stop.setVisibility(Button.GONE);
            }
        });
    }

    public static class BadOriginalImageException extends Exception {}
    public static class DestinationFileExistsException extends Exception {}
    public static class CopyAttributesFailedException extends Exception {
        public CopyAttributesFailedException(Throwable err) {
            super(err);
        }
    }

}
