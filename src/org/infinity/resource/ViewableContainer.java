// Near Infinity - An Infinity Engine Browser and Editor
// Copyright (C) 2001 - 2022 Jon Olav Hauglid
// See LICENSE.txt for license information

package org.infinity.resource;

import org.infinity.gui.StatusBar;

public interface ViewableContainer {
  StatusBar getStatusBar();

  Viewable getViewable();

  void setViewable(Viewable vieweable);
}
