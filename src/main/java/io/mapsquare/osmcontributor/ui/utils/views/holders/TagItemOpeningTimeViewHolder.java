/**
 * Copyright (C) 2016 eBusiness Information
 *
 * This file is part of OSM Contributor.
 *
 * OSM Contributor is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * OSM Contributor is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with OSM Contributor.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.mapsquare.osmcontributor.ui.utils.views.holders;


import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.mapsquare.osmcontributor.R;

public class TagItemOpeningTimeViewHolder extends RecyclerView.ViewHolder {
    private View poiTagLayout;

    @BindView(R.id.opening_time_list_view)
    RecyclerView openingTimeRecyclerView;

    @BindView(R.id.poi_key)
    TextView textViewKey;

    @BindView(R.id.tag_item_opening_time_add_button)
    View addButton;

    @BindView(R.id.content_layout)
    LinearLayout content;

    public TagItemOpeningTimeViewHolder(View v) {
        super(v);
        poiTagLayout = v;
        ButterKnife.bind(this, v);
    }

    public View getPoiTagLayout() {
        return poiTagLayout;
    }

    public TextView getTextViewKey() {
        return textViewKey;
    }

    public RecyclerView getOpeningTimeRecyclerView() {
        return openingTimeRecyclerView;
    }

    public View getAddButton() {
        return addButton;
    }

    public LinearLayout getContent() {
        return content;
    }
}