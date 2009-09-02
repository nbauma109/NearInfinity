// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.wmp;

import infinity.datatype.*;
import infinity.resource.*;

import javax.swing.*;

final class MapEntry extends AbstractStruct implements HasAddRemovable, HasDetailViewer
{
  MapEntry(AbstractStruct superStruct, byte buffer[], int offset, int nr) throws Exception
  {
    super(superStruct, "Map entry " + nr, buffer, offset);
  }

// --------------------- Begin Interface HasAddRemovable ---------------------

  public AddRemovable[] getAddRemovables() throws Exception
  {
    return new AddRemovable[]{new AreaEntry()};
  }

// --------------------- End Interface HasAddRemovable ---------------------


// --------------------- Begin Interface HasDetailViewer ---------------------

  public JComponent getDetailViewer()
  {
    return new ViewerMap(this);
  }

// --------------------- End Interface HasDetailViewer ---------------------

  protected int read(byte buffer[], int offset) throws Exception
  {
    list.add(new ResourceRef(buffer, offset, "Map", "MOS"));
    list.add(new DecNumber(buffer, offset + 8, 4, "Width"));
    list.add(new DecNumber(buffer, offset + 12, 4, "Height"));
    list.add(new Unknown(buffer, offset + 16, 4));
    list.add(new StringRef(buffer, offset + 20, "Name"));
    list.add(new Unknown(buffer, offset + 24, 4));
    list.add(new Unknown(buffer, offset + 28, 4));
    SectionCount area_count = new SectionCount(buffer, offset + 32, 4, "# area entries",
                                               AreaEntry.class);
    list.add(area_count);
    SectionOffset area_offset = new SectionOffset(buffer, offset + 36, "Area entries offset",
                                                  AreaEntry.class);
    list.add(area_offset);
    HexNumber link_offset = new HexNumber(buffer, offset + 40, 4, "Area link entries offset");
    list.add(link_offset);
    DecNumber link_count = new DecNumber(buffer, offset + 44, 4, "# area link entries");
    list.add(link_count);
    list.add(new ResourceRef(buffer, offset + 48, "Map icons", "BAM"));
    list.add(new Unknown(buffer, offset + 56, 128));

    for (int i = 0; i < area_count.getValue(); i++) {
      AreaEntry areaEntry = new AreaEntry(this, buffer, area_offset.getValue() + 240 * i, i);
      list.add(areaEntry);
      areaEntry.readLinks(buffer, link_offset);
    }

    return offset + 128 + 56;
  }

  protected void datatypeAddedInChild(AbstractStruct child, AddRemovable datatype)
  {
    if (datatype instanceof AreaLink) {
      DecNumber linkCount = (DecNumber)getAttribute("# area link entries");
      linkCount.setValue(linkCount.getValue() + 1);
    }
  }

  protected void datatypeRemovedInChild(AbstractStruct child, AddRemovable datatype)
  {
    if (datatype instanceof AreaLink) {
      DecNumber linkCount = (DecNumber)getAttribute("# area link entries");
      linkCount.setValue(linkCount.getValue() - 1);
    }
  }
}

