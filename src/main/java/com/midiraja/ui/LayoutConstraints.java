/*
 * Copyright (c) 2026, Park, Sungchul All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the LICENSE file in the root
 * directory of this source tree.
 */

package com.midiraja.ui;

/**
 * Immutable record representing the spatial constraints and display hints allocated to a Panel by
 * the LayoutManager.
 *
 * @param width The number of columns the panel should occupy.
 * @param height The number of rows the panel should occupy.
 * @param showHeaders Whether the panel should render its decorative header (e.g., "[CONTROLS]").
 * @param isHorizontal Whether the panel is in horizontal layout mode (specific to
 *        ChannelActivityPanel).
 */
public record LayoutConstraints(int width, int height, boolean showHeaders, boolean isHorizontal)
{
}
