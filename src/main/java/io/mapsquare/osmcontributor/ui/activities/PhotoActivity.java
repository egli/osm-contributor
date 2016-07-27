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
package io.mapsquare.osmcontributor.ui.activities;

import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.ProgressBar;

import com.facebook.drawee.view.SimpleDraweeView;
import com.flickr4java.flickr.photos.Size;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.mapsquare.osmcontributor.OsmTemplateApplication;
import io.mapsquare.osmcontributor.R;
import io.mapsquare.osmcontributor.flickr.event.PhotosFoundEvent;
import io.mapsquare.osmcontributor.flickr.rest.GetFlickrPhotos;
import io.mapsquare.osmcontributor.ui.adapters.ImageAdapter;

public class PhotoActivity extends AppCompatActivity {

    /*=========================================*/
    /*------------CONSTANTS--------------------*/
    /*=========================================*/
    private static final int NB_IMAGE_REQUESTED = 35;

    private static final int NB_PAGE_REQUESTED = 1;

    /*=========================================*/
    /*---------------VIEWS---------------------*/
    /*=========================================*/
    @BindView(R.id.grid_photos)
    GridView gridPhotos;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.loading)
    ProgressBar loadingImage;

    @BindView(R.id.add_photo)
    FloatingActionButton addPhoto;

    @BindView(R.id.zoom_photo)
    SimpleDraweeView zoomPhoto;

    /*=========================================*/
    /*------------ATTRIBUTES-------------------*/
    /*=========================================*/
    private ImageAdapter imageAdapter;

    private int lastVisiblePos;

    private Long poiId;

    private GetFlickrPhotos asyncGetPhotos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_photo);
        ButterKnife.bind(this);

        // Set action bar infos.
        toolbar.setTitle("Photos");
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Loading image.
        addPhoto.setVisibility(View.VISIBLE);

        // Hide button on scroll down and show it on scroll up.
        lastVisiblePos = gridPhotos.getFirstVisiblePosition();
        gridPhotos.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) { }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                int currentFirstVisPos = view.getFirstVisiblePosition();
                // Scroll down.
                if (currentFirstVisPos > lastVisiblePos) {
                    addPhoto.setVisibility(View.INVISIBLE);
                }

                // Scroll up.
                if (currentFirstVisPos < lastVisiblePos) {
                    addPhoto.setVisibility(View.VISIBLE);
                }
                lastVisiblePos = currentFirstVisPos;
            }
        });

        gridPhotos.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                zoomPhoto.setImageURI(Uri.parse(ImageAdapter.getPhotosOriginals(poiId).get(position)));
                zoomPhoto.setVisibility(View.VISIBLE);
            }
        });

        zoomPhoto.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                zoomPhoto.setVisibility(View.INVISIBLE);
            }
        });

        // Init parameters.
        OsmTemplateApplication application = (OsmTemplateApplication) getApplication();
        double latitude = getIntent().getDoubleExtra("latitude", 0);
        double longitude = getIntent().getDoubleExtra("longitude", 0);
        poiId = getIntent().getLongExtra("poiId", 0);
        EventBus.getDefault().register(this);
        imageAdapter = new ImageAdapter(this, poiId);
        gridPhotos.setAdapter(imageAdapter);

        // If some picture are cached, show them.
        if (ImageAdapter.getPhotoUrlsCachedThumbs(poiId) == null || ImageAdapter.getPhotoUrlsCachedThumbs(poiId).isEmpty()) {
            gridPhotos.setVisibility(View.INVISIBLE);
            loadingImage.setVisibility(View.VISIBLE);
        }

        // Check updates or get photos for the first time.
        asyncGetPhotos = new GetFlickrPhotos(longitude, latitude, application.getFlickr(), NB_IMAGE_REQUESTED, NB_PAGE_REQUESTED);
        asyncGetPhotos.execute();
    }

    /*=========================================*/
    /*---------------EVENTS--------------------*/
    /*=========================================*/
    /**
     * Event called when GetFlickrPhotos AsyncTask is done.
     * @param photosFoundEvent event with photos found
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onPhotosFoundEvent(PhotosFoundEvent photosFoundEvent) {
        List<List<Size>> photos = photosFoundEvent.getPhotos();
        if (photos != null && !photos.isEmpty()) {
            for (List<Size> size : photos) {
                imageAdapter.addPhoto(size.get(Size.SQUARE).getSource(), poiId, Size.SQUARE);
                imageAdapter.addPhoto(size.get(Size.ORIGINAL).getSource(), poiId, Size.ORIGINAL);
            }
        }
        loadingImage.setVisibility(View.INVISIBLE);
        gridPhotos.setVisibility(View.VISIBLE);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (zoomPhoto.getVisibility() == View.VISIBLE) {
            zoomPhoto.setVisibility(View.INVISIBLE);
        } else {
            if (asyncGetPhotos != null) {
                asyncGetPhotos.cancel(true);
            }
            onBackPressed();
        }
        return true;
    }
}