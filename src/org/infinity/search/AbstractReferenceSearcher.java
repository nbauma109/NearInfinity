// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.search;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;

import org.infinity.datatype.TextString;
import org.infinity.gui.Center;
import org.infinity.gui.ChildFrame;
import org.infinity.gui.ViewerUtil;
import org.infinity.icon.Icons;
import org.infinity.resource.Resource;
import org.infinity.resource.ResourceFactory;
import org.infinity.resource.StructEntry;
import org.infinity.resource.cre.CreResource;
import org.infinity.resource.key.ResourceEntry;

abstract class AbstractReferenceSearcher extends AbstractSearcher implements Runnable, ActionListener
{
  protected static final String[] FILE_TYPES = {"2DA", "ARE", "BCS", "CHR", "CHU", "CRE", "DLG",
                                                "EFF", "GAM", "INI", "ITM", "PRO", "SAV", "SPL",
                                                "STO", "VEF", "VVC", "WED", "WMP"};

  private static HashMap<String, boolean[]> lastSelection = new HashMap<>();

  /** Searched entry. */
  protected final ResourceEntry targetEntry;

  private final ChildFrame selectframe = new ChildFrame("References", true);
  private final JButton bStart = new JButton("Search", Icons.getIcon(Icons.ICON_FIND_16));
  private final JButton bCancel = new JButton("Cancel", Icons.getIcon(Icons.ICON_DELETE_16));
  private final JButton bInvert = new JButton("Invert");
  private final JButton bClear = new JButton("Clear");
  private final JButton bSet = new JButton("Set");
  private final JButton bDefault = new JButton("Default");
  /** Window with results of search. */
  private final ReferenceHitFrame hitFrame;
  /** Extensions of files with resources in which it is possible to search. */
  private final String[] filetypes;
  private final boolean[] preselect;
  /** Optional alternate name to search for. */
  protected String targetEntryName;
  private JCheckBox[] boxes;
  private List<ResourceEntry> files;

  AbstractReferenceSearcher(ResourceEntry targetEntry, String filetypes[], Component parent)
  {
    this(targetEntry, filetypes, setSelectedFileTypes(targetEntry, filetypes), parent);
  }

  AbstractReferenceSearcher(ResourceEntry targetEntry, String filetypes[], boolean[] preselect, Component parent)
  {
    super(SEARCH_MULTI_TYPE_FORMAT, parent);
    this.targetEntry = targetEntry;
    if (targetEntry != null && "CRE".equalsIgnoreCase(targetEntry.getExtension())) {
      try {
        CreResource cre = new CreResource(targetEntry);
        StructEntry nameEntry = cre.getAttribute(CreResource.CRE_SCRIPT_NAME);
        if (nameEntry instanceof TextString) {
          targetEntryName = ((TextString)nameEntry).toString().trim();
          // ignore specific script names
          if (targetEntryName.isEmpty() || targetEntryName.equalsIgnoreCase("None")) {
            targetEntryName = null;
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    this.filetypes = filetypes;
    this.preselect = preselect;
    hitFrame = new ReferenceHitFrame(targetEntry, parent);
    if (filetypes.length == 1) {
      files = new ArrayList<>();
      files.addAll(ResourceFactory.getResources(filetypes[0]));
      if (!files.isEmpty()) {
        new Thread(this).start();
      }
    }
    else {
      boxes = new JCheckBox[filetypes.length];
      bStart.setMnemonic('s');
      bCancel.setMnemonic('c');
      bInvert.setMnemonic('i');
      bDefault.setMnemonic('d');
      bStart.addActionListener(this);
      bCancel.addActionListener(this);
      bInvert.addActionListener(this);
      bClear.addActionListener(this);
      bSet.addActionListener(this);
      bDefault.addActionListener(this);
      selectframe.getRootPane().setDefaultButton(bStart);
      selectframe.setIconImage(Icons.getIcon(Icons.ICON_FIND_16).getImage());

      JPanel boxpanel = new JPanel(new GridLayout(0, 2, 3, 3));
      for (int i = 0; i < boxes.length; i++) {
        boxes[i] = new JCheckBox(filetypes[i], isPreselected(filetypes, preselect, i));
        boxpanel.add(boxes[i]);
      }
      boxpanel.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 0));
      boolean[] selection = lastSelection.get(getTargetExtension());
      if (selection != null) {
        for (int i = 0; i < selection.length; i++) {
          boxes[i].setSelected(selection[i]);
        }
      }

      GridBagConstraints gbc = new GridBagConstraints();
      JPanel ipanel1 = new JPanel(new GridBagLayout());
      gbc = ViewerUtil.setGBC(gbc, 0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(0, 0, 0, 0), 0, 0);
      ipanel1.add(bClear, gbc);
      gbc = ViewerUtil.setGBC(gbc, 1, 0, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(0, 8, 0, 0), 0, 0);
      ipanel1.add(bSet, gbc);
      gbc = ViewerUtil.setGBC(gbc, 0, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(4, 0, 0, 0), 0, 0);
      ipanel1.add(bDefault, gbc);
      gbc = ViewerUtil.setGBC(gbc, 1, 1, 1, 1, 0.0, 0.0, GridBagConstraints.FIRST_LINE_START,
                              GridBagConstraints.HORIZONTAL, new Insets(4, 8, 0, 0), 0, 0);
      ipanel1.add(bInvert, gbc);
      JPanel ipanel = new JPanel(new BorderLayout());
      ipanel.add(ipanel1, BorderLayout.CENTER);

      JPanel innerpanel = new JPanel(new BorderLayout());
      innerpanel.add(boxpanel, BorderLayout.CENTER);
      innerpanel.add(ipanel, BorderLayout.SOUTH);
      innerpanel.setBorder(BorderFactory.createTitledBorder("Select files to search:"));

      JPanel bpanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
      bpanel.add(bStart);
      bpanel.add(bCancel);

      JPanel mainpanel = new JPanel(new BorderLayout());
      mainpanel.add(innerpanel, BorderLayout.CENTER);
      mainpanel.add(bpanel, BorderLayout.SOUTH);
      mainpanel.setBorder(BorderFactory.createEmptyBorder(3, 3, 3, 3));

      JPanel pane = (JPanel)selectframe.getContentPane();
      pane.setLayout(new BorderLayout());
      pane.add(mainpanel, BorderLayout.CENTER);

      selectframe.pack();
      Center.center(selectframe, parent.getBounds());
      selectframe.setVisible(true);
    }
  }

// --------------------- Begin Interface ActionListener ---------------------

  @Override
  public void actionPerformed(ActionEvent event)
  {
    if (event.getSource() == bStart) {
      selectframe.setVisible(false);
      files = new ArrayList<>();
      final String ext = getTargetExtension();
      boolean[] selection = lastSelection.get(ext);
      if (selection == null) {
        selection = new boolean[filetypes.length];
        lastSelection.put(ext, selection);
      }
      for (int i = 0; i < filetypes.length; i++) {
        if (boxes[i].isSelected()) {
          files.addAll(ResourceFactory.getResources(filetypes[i]));
        }
        selection[i] = boxes[i].isSelected();
      }
      if (files.size() > 0) {
        new Thread(this).start();
      }
    }
    else if (event.getSource() == bCancel) {
      selectframe.setVisible(false);
    }
    else if (event.getSource() == bInvert) {
      for (final JCheckBox box: boxes) {
        box.setSelected(!box.isSelected());
      }
    }
    else if (event.getSource() == bClear) {
      for (final JCheckBox box: boxes) {
        box.setSelected(false);
      }
    }
    else if (event.getSource() == bSet) {
      for (final JCheckBox box: boxes) {
        box.setSelected(true);
      }
    }
    else if (event.getSource() == bDefault) {
      if (preselect != null) {
        for (int i = 0; i < boxes.length; i++) {
          if (preselect != null) {
            boxes[i].setSelected((i < preselect.length) ? preselect[i] : false);
          } else {
            boxes[i].setSelected(true);
          }
        }
      }
    }
  }

// --------------------- End Interface ActionListener ---------------------


// --------------------- Begin Interface Runnable ---------------------

  @Override
  public void run()
  {
    // executing multithreaded search
    if (runSearch("Searching", files)) {
      hitFrame.close();
      return;
    }
    hitFrame.setVisible(true);
  }

// --------------------- End Interface Runnable ---------------------

  @Override
  protected Runnable newWorker(ResourceEntry entry) {
    return () -> {
      final Resource resource = ResourceFactory.getResource(entry);
      if (resource != null) {
        search(entry, resource);
      }
      advanceProgress();
    };
  }

  synchronized void addHit(ResourceEntry entry, String name, StructEntry ref)
  {
    hitFrame.addHit(entry, name, ref);
  }

  /**
   * Performs actual search necessary information in the resource. Search procedure
   * must register their results by calling method {@link #addHit}.
   *
   * @param entry Pointer to the resource in which search is performed
   * @param resource Loaded resource that corresponds to the {@code entry}
   */
  abstract void search(ResourceEntry entry, Resource resource);

  ResourceEntry getTargetEntry()
  {
    return targetEntry;
  }

  private String getTargetExtension()
  {
    return (targetEntry != null) ? targetEntry.getExtension() : "";
  }

  private boolean isPreselected(String[] filetypes, boolean[] preselect, int index)
  {
    boolean retVal = true;
    if (index >= 0 && filetypes != null && filetypes.length > index) {
      if (preselect != null && preselect.length > index && !preselect[index]) {
        retVal = false;
      }
    }
    return retVal;
  }

  /**
   * Determines default set of extensions in which search must be performed based
   * on the {@code entry} extension.
   *
   * @param entry Searched entry. If {@code null}, method returns {@code null}
   * @param filetypes List of the resource extensions in which search can be performed.
   *        Must not be {@code null}
   * @return An array with the same size as {@code filetypes}
   */
  private static boolean[] setSelectedFileTypes(ResourceEntry entry, String[] filetypes)
  {
    if (entry == null) { return null; }

    final boolean[] retVal = new boolean[filetypes.length];
    String[] selectedExt = null;
    final String ext = entry.getExtension();
    if ("2DA".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"BCS", "CRE", "DLG", "EFF", "ITM", "SPL"};
    } else if ("ARE".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"2DA", "BCS", "DLG", "GAM", "WMP"};
    } else if ("BAM".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"2DA", "ARE", "BCS", "CHU", "CRE", "DLG", "EFF",
                                 "GAM", "INI", "ITM", "PRO", "SPL", "VEF", "VVC", "WMP"};
    } else if ("BMP".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"2DA", "ARE", "BCS", "CHU", "CRE", "DLG", "EFF", "GAM",
                                 "INI", "ITM", "PRO", "SPL", "VEF", "VVC"};
    } else if ("CRE".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"2DA", "ARE", "BCS", "DLG", "EFF", "GAM", "INI", "ITM", "SPL"};
    } else if ("DLG".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"2DA", "BCS", "CRE", "DLG", "GAM"};
    } else if ("EFF".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"CRE", "EFF", "GAM", "ITM", "SPL"};
    } else if ("FNT".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"CHU"};
    } else if ("ITM".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"2DA", "ARE", "BCS", "CRE", "DLG", "EFF", "GAM", "ITM",
                                 "SPL", "STO"};
    } else if ("MOS".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"2DA", "ARE", "BCS", "CHU", "DLG", "WMP"};
    } else if ("MVE".equalsIgnoreCase(ext) || "WBM".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"ARE", "BCS", "CRE", "DLG", "EFF", "GAM", "ITM", "SPL"};
    } else if ("PNG".equalsIgnoreCase(ext)) {
      // TODO: confirm!
      selectedExt = new String[]{"2DA", "ARE", "BCS", "CHU", "CRE", "DLG", "EFF", "GAM",
                                 "INI", "ITM", "PRO", "SPL", "VEF", "VVC"};
    } else if ("PRO".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"ARE", "CRE", "EFF", "GAM", "ITM", "SPL"};
    } else if ("PVRZ".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"BAM", "MOS", "TIS"};
    } else if ("SPL".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"2DA", "ARE", "BCS", "CRE", "DLG", "EFF", "GAM",
                                 "ITM", "SPL"};
    } else if ("STO".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"BCS", "DLG"};
    } else if ("TIS".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"WED"};
    } else if ("VVC".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"ARE", "BCS", "DLG", "EFF", "GAM", "INI", "ITM", "PRO",
                                 "SPL", "VEF", "VVC"};
    } else if ("WAV".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"2DA", "ARE", "BCS", "CRE", "DLG", "EFF", "GAM", "INI",
                                 "ITM", "PRO", "SPL", "VEF", "VVC"};
    } else if ("WED".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"ARE"};
    } else if ("WMP".equalsIgnoreCase(ext)) {
      selectedExt = new String[]{"BCS", "DLG"};
    }

    // defining preselection
    if (selectedExt != null && selectedExt.length > 0) {
      for (int i = 0; i < retVal.length; i++) {
        for (String e : selectedExt) {
          if (e.equalsIgnoreCase(filetypes[i])) {
            retVal[i] = true;
            break;
          }
        }
      }
    } else {
      // select all by default
      Arrays.fill(retVal, true);
    }
    return retVal;
  }
}
