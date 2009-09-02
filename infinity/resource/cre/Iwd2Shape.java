// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2005 Jon Olav Hauglid
// See LICENSE.txt for license information

package infinity.resource.cre;

import infinity.datatype.*;
import infinity.resource.AbstractStruct;
import infinity.resource.AddRemovable;

public final class Iwd2Shape extends AbstractStruct implements AddRemovable
{
  public Iwd2Shape() throws Exception
  {
    super(null, "Shape", new byte[16], 0);
  }

  public Iwd2Shape(AbstractStruct superStruct, byte buffer[], int offset) throws Exception
  {
    super(superStruct, "Shape", buffer, offset);
  }

  protected int read(byte buffer[], int offset) throws Exception
  {
    list.add(new IwdRef(buffer, offset, "ResRef", "LISTSHAP.2DA"));
    list.add(new DecNumber(buffer, offset + 4, 4, "# memorizable"));
    list.add(new DecNumber(buffer, offset + 8, 4, "# remaining"));
    list.add(new Unknown(buffer, offset + 12, 4));
    return offset + 16;
  }
}

