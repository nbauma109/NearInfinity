// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2022 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.gui.converter;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;

import org.infinity.gui.ChildFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.gui.WindowBlocker;
import org.infinity.icon.Icons;
import org.infinity.resource.Profile;
import org.infinity.resource.graphics.ColorConvert;
import org.infinity.resource.graphics.Compressor;
import org.infinity.resource.graphics.DxtEncoder;
import org.infinity.util.DynamicArray;
import org.infinity.util.SimpleListModel;
import org.infinity.util.io.FileEx;
import org.infinity.util.io.FileManager;
import org.infinity.util.io.StreamUtils;

public class ConvertToPvrz extends ChildFrame implements ActionListener, PropertyChangeListener {
  private static String currentDir = Profile.getGameRoot().toString();

  private JList<Path> lInputList;
  private SimpleListModel<Path> lInputModel;
  private JButton bConvert;
  private JButton bCancel;
  private JButton bInputAdd;
  private JButton bInputAddFolder;
  private JButton bInputRemove;
  private JButton bInputRemoveAll;
  private JButton bTargetDir;
  private JButton bCompressionHelp;
  private JTextField tfTargetDir;
  private JComboBox<String> cbOverwrite;
  private JComboBox<String> cbCompression;
  private JCheckBox cbCloseOnExit;
  private SwingWorker<List<String>, Void> workerConvert;
  private ProgressMonitor progress;
  private WindowBlocker blocker;

  // Returns a list of supported graphics file formats
  private static FileNameExtensionFilter[] getInputFilters() {
    FileNameExtensionFilter[] filters = new FileNameExtensionFilter[] {
        new FileNameExtensionFilter("Graphics files (*.bmp, *.png, *,jpg, *.jpeg, *.pvr)", "bam", "bmp", "png", "jpg",
            "jpeg", "pvr"),
        new FileNameExtensionFilter("BMP files (*.bmp)", "bmp"),
        new FileNameExtensionFilter("PNG files (*.png)", "png"),
        new FileNameExtensionFilter("JPEG files (*.jpg, *.jpeg)", "jpg", "jpeg"),
        new FileNameExtensionFilter("PVR files (*.pvr)", "pvr") };
    return filters;
  }

  /**
   * Creates a PVR header based on the parameters specified.
   *
   * @param width       Texture width in pixels.
   * @param height      Texture height in pixels.
   * @param pixelFormat Internal pixel format code.
   * @return The PVR header as byte array.
   */
  public static byte[] createPVRHeader(int width, int height, int pixelFormat) {
    byte[] header = new byte[0x34];
    DynamicArray.putInt(header, 0, 0x03525650); // signature
    DynamicArray.putInt(header, 4, 0); // flags
    DynamicArray.putInt(header, 8, pixelFormat); // pixel format
    DynamicArray.putInt(header, 12, 0); // pixel format (extension)
    DynamicArray.putInt(header, 16, 0); // color space (0=linear rgb)
    DynamicArray.putInt(header, 20, 0); // channel type (0=unsigned byte normalized)
    DynamicArray.putInt(header, 24, height); // height
    DynamicArray.putInt(header, 28, width); // width
    DynamicArray.putInt(header, 32, 1); // depth (in pixels)
    DynamicArray.putInt(header, 36, 1); // # surfaces
    DynamicArray.putInt(header, 40, 1); // # faces
    DynamicArray.putInt(header, 44, 1); // # mipmap levels
    DynamicArray.putInt(header, 48, 0); // # meta data size
    return header;
  }

  /**
   * Calculates the first available power of two value that is greater than the specified value.
   *
   * @param value The value that should fit into the resulting power of two value.
   * @return A power of two value.
   */
  public static int nextPowerOfTwo(int value) {
    int count = 0, pos = 0;
    for (int i = 0, tmp = value; i < 32; i++) {
      if ((tmp & 1) != 0) {
        count++;
        pos = i;
      }
      tmp >>>= 1;
    }

    if (count != 1) {
      value = (count < 1) ? 4 : (1 << (pos + 1));
    }
    return value;
  }

  public ConvertToPvrz() {
    super("Convert to PVRZ", true);
    init();
  }

  // --------------------- Begin Class ChildFrame ---------------------

  @Override
  protected boolean windowClosing(boolean forced) throws Exception {
    clear();
    return super.windowClosing(forced);
  }

  // --------------------- End Class ChildFrame ---------------------

  // --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == bConvert) {
      workerConvert = new SwingWorker<List<String>, Void>() {
        @Override
        public List<String> doInBackground() {
          // returns summary of the completed conversion
          return convert();
        }
      };
      workerConvert.addPropertyChangeListener(this);
      blocker = new WindowBlocker(this);
      blocker.setBlocked(true);
      workerConvert.execute();
    } else if (event.getSource() == bCancel) {
      hideWindow();
    } else if (event.getSource() == bInputAdd) {
      JFileChooser fc = new JFileChooser(currentDir);
      fc.setDialogTitle("Choose files");
      fc.setDialogType(JFileChooser.OPEN_DIALOG);
      fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
      fc.setMultiSelectionEnabled(true);
      FileNameExtensionFilter[] filters = getInputFilters();
      for (final FileNameExtensionFilter filter : filters) {
        fc.addChoosableFileFilter(filter);
      }
      fc.setFileFilter(filters[0]);
      int ret = fc.showOpenDialog(this);
      if (ret == JFileChooser.APPROVE_OPTION) {
        File[] files = fc.getSelectedFiles();
        if (files != null && files.length > 0) {
          currentDir = files[0].getParent();
          // add to list box
          for (final File f : files) {
            lInputModel.addElement(f.toPath());
          }
        }
      }
      fc = null;
      bConvert.setEnabled(isReady());
    } else if (event.getSource() == bInputAddFolder) {
      JFileChooser fc = new JFileChooser(currentDir);
      fc.setDialogTitle("Choose directory");
      fc.setDialogType(JFileChooser.OPEN_DIALOG);
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      int ret = fc.showOpenDialog(this);
      if (ret == JFileChooser.APPROVE_OPTION) {
        // adding all files in the directory
        File dir = fc.getSelectedFile();
        if (dir != null && dir.isDirectory()) {
          currentDir = dir.toString();
          FileNameExtensionFilter[] filters = getInputFilters();
          File[] fileList = dir.listFiles();
          for (final File file : fileList) {
            for (final FileNameExtensionFilter filter : filters) {
              if (file != null && file.isFile() && filter.accept(file)) {
                lInputModel.addElement(file.toPath());
                break;
              }
            }
          }
        }
      }
      fc = null;
      bConvert.setEnabled(isReady());
    } else if (event.getSource() == bInputRemove) {
      int[] indices = lInputList.getSelectedIndices();
      for (int i = indices.length - 1; i >= 0; i--) {
        lInputModel.remove(indices[i]);
      }
      bConvert.setEnabled(isReady());
    } else if (event.getSource() == bInputRemoveAll) {
      lInputModel.clear();
      bConvert.setEnabled(isReady());
    } else if (event.getSource() == bTargetDir) {
      JFileChooser fc = new JFileChooser(currentDir);
      fc.setDialogTitle("Choose target directory");
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      if (!tfTargetDir.getText().isEmpty()) {
        fc.setSelectedFile(FileManager.resolve(tfTargetDir.getText()).toFile());
      }
      int ret = fc.showOpenDialog(this);
      if (ret == JFileChooser.APPROVE_OPTION) {
        currentDir = fc.getSelectedFile().toString();
        tfTargetDir.setText(fc.getSelectedFile().toString());
      }
      fc = null;
      bConvert.setEnabled(isReady());
    } else if (event.getSource() == bCompressionHelp) {
      final String helpMsg = "\"DXT1\" provides the highest compression ratio. It supports only 1 bit alpha\n"
          + "(i.e. either no or full transparency) and is the preferred type for TIS or MOS resources.\n\n"
          + "\"DXT5\" provides an average compression ratio. It features interpolated\n"
          + "alpha transitions and is the preferred type for BAM resources.\n\n"
          + "\"Auto\" selects the most appropriate compression type based on the input data.";
      JOptionPane.showMessageDialog(this, helpMsg, "About Compression Types", JOptionPane.INFORMATION_MESSAGE);
    }
  }

  // --------------------- End Interface ActionListener ---------------------

  // --------------------- Begin Interface PropertyChangeListener ---------------------

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (event.getSource() == workerConvert) {
      if ("state".equals(event.getPropertyName()) && SwingWorker.StateValue.DONE == event.getNewValue()) {
        if (blocker != null) {
          blocker.setBlocked(false);
          blocker = null;
        }
        if (progress != null) {
          progress.close();
          progress = null;
        }
        List<String> sl = null;
        try {
          sl = workerConvert.get();
        } catch (Exception e) {
          e.printStackTrace();
        }
        workerConvert = null;

        boolean isError = false;
        String s = null;
        if (sl != null && !sl.isEmpty()) {
          if (sl.get(0) != null) {
            s = sl.get(0);
          } else if (sl.size() > 1 && sl.get(1) != null) {
            s = sl.get(1);
            isError = true;
          }
        }
        if (s != null) {
          if (isError) {
            JOptionPane.showMessageDialog(this, s, "Error", JOptionPane.ERROR_MESSAGE);
          } else {
            JOptionPane.showMessageDialog(this, s, "Information", JOptionPane.INFORMATION_MESSAGE);
            if (cbCloseOnExit.isSelected()) {
              hideWindow();
            } else {
              clear();
            }
          }
        } else {
          JOptionPane.showMessageDialog(this, "Unknown error!", "Error", JOptionPane.ERROR_MESSAGE);
        }
      }
    }
  }

  // --------------------- End Interface PropertyChangeListener ---------------------

  private void init() {
    setIconImage(Icons.ICON_APPLICATION_16.getIcon().getImage());

    // setting up input section
    JPanel pInputAdd = new JPanel(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    bInputAdd = new JButton("Add...");
    bInputAdd.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 4, 0), 16, 0);
    pInputAdd.add(bInputAdd, c);

    bInputAddFolder = new JButton("Add folder...");
    bInputAddFolder.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 16, 0);
    pInputAdd.add(bInputAddFolder, c);

    JPanel pInputRemove = new JPanel(new GridBagLayout());
    bInputRemove = new JButton("Remove");
    bInputRemove.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 4, 0), 16, 0);
    pInputRemove.add(bInputRemove, c);

    bInputRemoveAll = new JButton("Remove all");
    bInputRemoveAll.addActionListener(this);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(4, 0, 0, 0), 16, 0);
    pInputRemove.add(bInputRemoveAll, c);

    JPanel pInputCtrl = new JPanel(new BorderLayout());
    pInputCtrl.add(pInputAdd, BorderLayout.WEST);
    pInputCtrl.add(pInputRemove, BorderLayout.EAST);

    lInputModel = new SimpleListModel<>();
    lInputList = new JList<>(lInputModel);
    lInputList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    JScrollPane scroll = new JScrollPane(lInputList);

    JPanel pInput = new JPanel(new GridBagLayout());
    pInput.setBorder(BorderFactory.createTitledBorder("Input "));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.BOTH,
        new Insets(0, 4, 0, 4), 0, 0);
    pInput.add(scroll, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 4, 4, 4), 0, 0);
    pInput.add(pInputCtrl, c);

    // setting up output section
    JLabel lTargetDir = new JLabel("Directory:");
    JLabel lOverwrite = new JLabel("Overwrite:");
    JLabel lCompression = new JLabel("Compression type:");
    tfTargetDir = new JTextField();
    bTargetDir = new JButton("...");
    bTargetDir.addActionListener(this);
    cbOverwrite = new JComboBox<>(new String[] { "Ask", "Replace", "Skip" });
    cbOverwrite.setSelectedIndex(1);
    cbCompression = new JComboBox<>(new String[] { "Auto", "DXT1", "DXT5" });
    cbCompression.setSelectedIndex(0);
    bCompressionHelp = new JButton("?");
    bCompressionHelp.setToolTipText("About compression types");
    bCompressionHelp.addActionListener(this);
    bCompressionHelp.setMargin(new Insets(bCompressionHelp.getMargin().top, 4, bCompressionHelp.getMargin().bottom, 4));

    JPanel pOutputSub = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 8, 0);
    pOutputSub.add(cbOverwrite, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 16, 0, 0), 0, 0);
    pOutputSub.add(lCompression, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 8, 0);
    pOutputSub.add(cbCompression, c);
    c = ViewerUtil.setGBC(c, 3, 0, 2, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pOutputSub.add(bCompressionHelp, c);

    JPanel pOutput = new JPanel(new GridBagLayout());
    pOutput.setBorder(BorderFactory.createTitledBorder("Output "));
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pOutput.add(lTargetDir, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 8, 0, 0), 0, 0);
    pOutput.add(tfTargetDir, c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 4), 0, 0);
    pOutput.add(bTargetDir, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pOutput.add(lOverwrite, c);
    c = ViewerUtil.setGBC(c, 1, 1, GridBagConstraints.REMAINDER, 1, 1.0, 0.0, GridBagConstraints.LINE_START,
        GridBagConstraints.NONE, new Insets(4, 8, 4, 0), 0, 0);
    pOutput.add(pOutputSub, c);

    // setting up bottom button bar
    cbCloseOnExit = new JCheckBox("Close dialog after conversion", true);
    bConvert = new JButton("Start Conversion");
    bConvert.addActionListener(this);
    bConvert.setEnabled(isReady());
    Insets i = bConvert.getInsets();
    bConvert.setMargin(new Insets(i.top + 2, i.left, i.bottom + 2, i.right));
    bCancel = new JButton("Cancel");
    bCancel.addActionListener(this);
    i = bCancel.getInsets();
    bCancel.setMargin(new Insets(i.top + 2, i.left, i.bottom + 2, i.right));

    JPanel pButtons = new JPanel(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(cbCloseOnExit, c);
    c = ViewerUtil.setGBC(c, 1, 0, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(new JPanel(), c);
    c = ViewerUtil.setGBC(c, 2, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_END, GridBagConstraints.NONE,
        new Insets(0, 0, 0, 0), 0, 0);
    pButtons.add(bConvert, c);
    c = ViewerUtil.setGBC(c, 3, 0, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_END, GridBagConstraints.NONE,
        new Insets(0, 4, 0, 0), 0, 0);
    pButtons.add(bCancel, c);

    // putting all together
    setLayout(new GridBagLayout());
    c = ViewerUtil.setGBC(c, 0, 0, 1, 1, 1.0, 1.0, GridBagConstraints.LINE_START, GridBagConstraints.BOTH,
        new Insets(8, 8, 0, 8), 0, 0);
    add(pInput, c);
    c = ViewerUtil.setGBC(c, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 8, 0, 8), 0, 0);
    add(pOutput, c);
    c = ViewerUtil.setGBC(c, 0, 2, 1, 1, 1.0, 0.0, GridBagConstraints.LINE_START, GridBagConstraints.HORIZONTAL,
        new Insets(8, 8, 8, 8), 0, 0);
    add(pButtons, c);

    // finalizing dialog initialization
    pack();
    setMinimumSize(getPreferredSize());
    setLocationRelativeTo(getParent());
    setVisible(true);
  }

  private void hideWindow() {
    clear();
    setVisible(false);
  }

  // resetting dialog state
  private void clear() {
    lInputModel.clear();
    bConvert.setEnabled(isReady());
  }

  // got enough data to start conversion?
  private boolean isReady() {
    return !lInputModel.isEmpty();
  }

  // checks graphics input file properties
  private static boolean isValidGraphicsInput(Path inFile) {
    boolean result = (inFile != null && FileEx.create(inFile).isFile());
    if (result) {
      Dimension d = ColorConvert.getImageDimension(inFile);
      if (d == null || d.width <= 0 || d.width > 1024 || d.height <= 0 || d.height > 1024) {
        result = false;
      }
    }
    return result;
  }

  // checks PVR input file properties
  private static boolean isValidPVRInput(Path inFile) {
    boolean result = (inFile != null && FileEx.create(inFile).isFile());
    if (result) {
      try (InputStream is = StreamUtils.getInputStream(inFile)) {
        String sig = StreamUtils.readString(is, 4);
        if ("PVR\u0003".equals(sig)) {
          StreamUtils.readInt(is); // flags
          StreamUtils.readInt(is); // pixel format #1
          StreamUtils.readInt(is); // pixel format #2
          StreamUtils.readInt(is); // color space
          StreamUtils.readInt(is); // channel type
          int h = StreamUtils.readInt(is); // height
          int w = StreamUtils.readInt(is); // width
          if (h <= 0 || w <= 0 || h > 1024 || w > 1024) {
            result = false;
          }
        } else {
          result = false;
        }
      } catch (IOException e) {
        result = false;
      }
    }
    return result;
  }

  // Convert source image(s) into the PVRZ format. Returns a short summary of the conversion process.
  // Return value: First list element is used for success message, second element for error message.
  private List<String> convert() {
    // fetching required information
    boolean ask = false, skip = false;
    switch (cbOverwrite.getSelectedIndex()) {
      case 0:
        ask = true;
        break;
      case 2:
        skip = true;
        break;
    }

    boolean auto = false;
    int dxt = 1;
    switch (cbCompression.getSelectedIndex()) {
      case 0:
        auto = true;
        break;
      case 2:
        dxt = 5;
        break;
    }

    Path targetPath = FileManager.resolve("");
    if (tfTargetDir.getText() != null && !tfTargetDir.getText().isEmpty()) {
      targetPath = FileManager.resolve(tfTargetDir.getText());
    }
    if (!FileEx.create(targetPath).isDirectory()) {
      List<String> l = new Vector<>(2);
      l.add(null);
      l.add("Invalid target directory specified. No conversion takes place.");
      return l;
    }

    if (lInputModel.isEmpty()) {
      List<String> l = new Vector<>(2);
      l.add(null);
      l.add("No source file(s) specified. No conversion takes place.");
      return l;
    }
    Path[] inputFiles = new Path[lInputModel.size()];
    for (int i = 0; i < lInputModel.size(); i++) {
      inputFiles[i] = lInputModel.get(i);
    }
    boolean isSingle = inputFiles.length == 1;

    // preparing progress meter
    final String note = "Converting file %d / %d";
    int progressIndex = 0, progressInc = 1;
    int progressMax = isSingle ? 100 : inputFiles.length;
    progress = new ProgressMonitor(this, "Converting PVRZ...", isSingle ? null : String.format(note, 0, progressMax), 0,
        progressMax + 1);
    progress.setMillisToDecideToPopup(500);
    progress.setMillisToPopup(2000);

    // starting conversion
    int skippedFiles = 0, warnings = 0, errors = 0;
    for (Path inFile : inputFiles) {
      progress.setProgress(progressIndex);
      if (!isSingle) {
        progress.setNote(String.format(note, progressIndex + 1, progressMax));
        progressIndex += progressInc;
      }
      boolean isGraphics = isValidGraphicsInput(inFile);
      boolean isPVR = isValidPVRInput(inFile);
      if (isGraphics || isPVR) {
        String inFileName = inFile.getFileName().toString();

        // generating output filename
        String outFileName = null;
        int n = inFileName.lastIndexOf('.');
        if (n > 0) {
          outFileName = inFileName.substring(0, n) + ".PVRZ";
        } else {
          outFileName = inFileName + ".PVRZ";
        }
        Path outFile = targetPath.resolve(outFileName);

        // handling overwrite existing file
        if (FileEx.create(outFile).exists()) {
          if (skip) {
            skippedFiles++;
            continue;
          } else if (ask) {
            String msg = String.format("File \"%s\" aready exists. Overwrite?", outFileName);
            int ret = JOptionPane.showConfirmDialog(this, msg, "Overwrite?", JOptionPane.YES_NO_OPTION);
            if (ret == JOptionPane.NO_OPTION) {
              skippedFiles++;
              continue;
            }
          }
        }

        // loading source image data
        if (isPVR) {
          // handling PVR files
          ByteBuffer bb = null;
          try (SeekableByteChannel ch = Files.newByteChannel(inFile, StandardOpenOption.READ)) {
            bb = StreamUtils.getByteBuffer((int) ch.size());
            ch.read(bb);
            bb.position(0);
          } catch (IOException e) {
            bb = null;
            errors++;
            e.printStackTrace();
          }
          if (bb != null) {
            byte[] buffer = bb.array();
            byte[] output = Compressor.compress(buffer, 0, buffer.length, true);
            try (OutputStream os = StreamUtils.getOutputStream(outFile, true)) {
              os.write(output);
            } catch (Exception e) {
              errors++;
              e.printStackTrace();
            }
          }
        } else if (isGraphics) {
          // handling graphics files
          BufferedImage srcImg = null;
          try {
            srcImg = ColorConvert.toBufferedImage(ImageIO.read(inFile.toFile()), true);
          } catch (Exception e) {
          }
          if (srcImg == null) {
            skippedFiles++;
            continue;
          }

          // handling "auto" compression format
          int[] pixels = ((DataBufferInt) srcImg.getRaster().getDataBuffer()).getData();
          if (auto) {
            for (n = 0; n < pixels.length; n++) {
              int alpha = pixels[n] >>> 24;
              if (alpha > 0x20 && alpha < 0xe0) {
                dxt = 5;
                break;
              }
            }
          }

          // ensure dimensions are always power of two
          int w = nextPowerOfTwo(srcImg.getWidth());
          int h = nextPowerOfTwo(srcImg.getHeight());
          if (w != srcImg.getWidth() || h != srcImg.getHeight()) {
            BufferedImage image = ColorConvert.createCompatibleImage(w, h, true);
            Graphics2D g = image.createGraphics();
            g.drawImage(srcImg, 0, 0, null);
            g.dispose();
            srcImg = image;
            pixels = ((DataBufferInt) srcImg.getRaster().getDataBuffer()).getData();
          }

          // preparing output
          DxtEncoder.DxtType dxtType = null;
          byte[] header = null;
          switch (dxt) {
            case 3:
              dxtType = DxtEncoder.DxtType.DXT3;
              header = createPVRHeader(w, h, 9);
              break;
            case 5:
              dxtType = DxtEncoder.DxtType.DXT5;
              header = createPVRHeader(w, h, 11);
              break;
            default:
              dxtType = DxtEncoder.DxtType.DXT1;
              header = createPVRHeader(w, h, 7);
          }

          // encoding block by block
          int outSize = DxtEncoder.calcImageSize(w, h, dxtType);
          byte[] output = new byte[outSize];
          int outOfs = 0;
          int bw = w / 4;
          int bh = h / 4;
          int[] inBlock = new int[16];
          byte[] outBlock = new byte[DxtEncoder.calcBlockSize(dxtType)];
          // more initialization for progress meter
          int counter = 0;
          if (isSingle) {
            progressInc = (bw * bh / 100);
            if (progressInc == 0) {
              progress.setMaximum(bw * bh + 1);
              progressInc = 1;
            }
          }
          for (int y = 0; y < bh; y++) {
            if (!isSingle) {
              // force the progress meter to pop up
              progress.setProgress(progressIndex);
            }
            for (int x = 0; x < bw; x++) {
              // handling progress meter
              if (isSingle) {
                if (counter >= progressInc) {
                  counter = 0;
                  progressIndex++;
                  progress.setProgress(progressIndex);
                }
                counter++;
              }
              if (progress.isCanceled()) {
                progress.close();
                progress = null;
                List<String> l = new Vector<>(2);
                l.add(null);
                l.add("Conversion cancelled.");
                return l;
              }

              // starting encoding process
              int ofs = y * w * 4 + x * 4;
              for (int i = 0; i < 4; i++, ofs += w) {
                System.arraycopy(pixels, ofs, inBlock, i * 4, 4);
              }
              try {
                DxtEncoder.encodeBlock(inBlock, outBlock, dxtType);
              } catch (Exception e) {
                warnings++;
                Arrays.fill(outBlock, (byte) 0);
              }
              System.arraycopy(outBlock, 0, output, outOfs, outBlock.length);
              outOfs += outBlock.length;
            }
          }

          // finalizing output data
          byte[] pvrz = new byte[header.length + output.length];
          System.arraycopy(header, 0, pvrz, 0, header.length);
          System.arraycopy(output, 0, pvrz, header.length, output.length);
          pvrz = Compressor.compress(pvrz, 0, pvrz.length, true);
          try (OutputStream os = StreamUtils.getOutputStream(outFile, true)) {
            os.write(pvrz);
          } catch (Exception e) {
            errors++;
            e.printStackTrace();
          }

          // cleaning up
          pixels = null;
          srcImg.flush();
          srcImg = null;
          output = null;
          pvrz = null;
          inBlock = null;
          outBlock = null;
          header = null;
        }
      } else {
        warnings++;
        skippedFiles++;
      }
    }
    progress.close();
    progress = null;

    // constructing failure/success message
    List<String> l = new Vector<>(2);
    StringBuilder sb = new StringBuilder();
    if (warnings == 0 && errors == 0) {
      sb.append("Conversion finished successfully.");
    } else {
      l.add(null);
      if (warnings > 0 && errors == 0) {
        sb.append(String.format("Conversion finished with %d warning(s).", warnings));
      } else {
        sb.append(String.format("Conversion finished with %d warning(s) and %d error(s).", warnings, errors));
      }
    }
    if (skippedFiles > 0) {
      if (skippedFiles == 1) {
        sb.append(String.format("\n%d file has been skipped.", skippedFiles));
      } else {
        sb.append(String.format("\n%d files have been skipped.", skippedFiles));
      }
    }
    l.add(sb.toString());

    return l;
  }
}
