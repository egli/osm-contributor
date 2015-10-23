/**
 * Copyright (C) 2015 eBusiness Information
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
package io.mapsquare.osmcontributor.core;

import android.app.Application;

import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.inject.Inject;

import de.greenrobot.event.EventBus;
import io.mapsquare.osmcontributor.core.database.DatabaseHelper;
import io.mapsquare.osmcontributor.core.database.dao.KeyWordDao;
import io.mapsquare.osmcontributor.core.database.dao.PoiDao;
import io.mapsquare.osmcontributor.core.database.dao.PoiNodeRefDao;
import io.mapsquare.osmcontributor.core.database.dao.PoiTagDao;
import io.mapsquare.osmcontributor.core.database.dao.PoiTypeDao;
import io.mapsquare.osmcontributor.core.database.dao.PoiTypeTagDao;
import io.mapsquare.osmcontributor.core.events.NodeRefAroundLoadedEvent;
import io.mapsquare.osmcontributor.core.events.PleaseLoadNodeRefAround;
import io.mapsquare.osmcontributor.core.events.PleaseLoadPoiForCreationEvent;
import io.mapsquare.osmcontributor.core.events.PleaseLoadPoiForEditionEvent;
import io.mapsquare.osmcontributor.core.events.PleaseLoadPoiTypes;
import io.mapsquare.osmcontributor.core.events.PleaseLoadPoisEvent;
import io.mapsquare.osmcontributor.core.events.PleaseLoadPoisToUpdateEvent;
import io.mapsquare.osmcontributor.core.events.PoiForEditionLoadedEvent;
import io.mapsquare.osmcontributor.core.events.PoiTypesLoaded;
import io.mapsquare.osmcontributor.core.events.PoisLoadedEvent;
import io.mapsquare.osmcontributor.core.events.PoisToUpdateLoadedEvent;
import io.mapsquare.osmcontributor.core.model.KeyWord;
import io.mapsquare.osmcontributor.core.model.Poi;
import io.mapsquare.osmcontributor.core.model.PoiNodeRef;
import io.mapsquare.osmcontributor.core.model.PoiTag;
import io.mapsquare.osmcontributor.core.model.PoiType;
import io.mapsquare.osmcontributor.core.model.PoiTypeTag;
import io.mapsquare.osmcontributor.map.events.ChangesInDB;
import io.mapsquare.osmcontributor.map.events.PleaseTellIfDbChanges;
import io.mapsquare.osmcontributor.upload.PoiUpdateWrapper;
import io.mapsquare.osmcontributor.utils.Box;
import timber.log.Timber;

import static io.mapsquare.osmcontributor.core.database.DatabaseHelper.loadLazyForeignCollection;

/**
 * Manager class for POIs.
 * Provides a number of methods to manipulate the POIs in the database that should be used instead
 * of calling the {@link io.mapsquare.osmcontributor.core.database.dao.PoiDao}.
 */
public class PoiManager {

    PoiDao poiDao;
    PoiTagDao poiTagDao;
    PoiNodeRefDao poiNodeRefDao;
    PoiTypeDao poiTypeDao;
    KeyWordDao keyWordDao;
    PoiTypeTagDao poiTypeTagDao;
    DatabaseHelper databaseHelper;
    ConfigManager configManager;
    EventBus bus;

    @Inject
    public PoiManager(PoiDao poiDao, PoiTagDao poiTagDao, PoiNodeRefDao poiNodeRefDao, PoiTypeDao poiTypeDao, KeyWordDao keyWordDao, PoiTypeTagDao poiTypeTagDao, DatabaseHelper databaseHelper, ConfigManager configManager, EventBus bus, Application application) {
        this.poiDao = poiDao;
        this.poiTagDao = poiTagDao;
        this.poiNodeRefDao = poiNodeRefDao;
        this.poiTypeDao = poiTypeDao;
        this.keyWordDao = keyWordDao;
        this.poiTypeTagDao = poiTypeTagDao;
        this.databaseHelper = databaseHelper;
        this.configManager = configManager;
        this.bus = bus;
    }

    // ********************************
    // ************ Events ************
    // ********************************

    public void onEventAsync(PleaseLoadNodeRefAround event) {
        bus.post(new NodeRefAroundLoadedEvent(poiNodeRefDao.queryAllInRect(event.getLat(), event.getLng())));
    }

    public void onEventAsync(PleaseLoadPoiForEditionEvent event) {
        loadPoiForEdition(event.getPoiId());
    }

    public void onEventAsync(PleaseLoadPoiForCreationEvent event) {
        loadPoiForCreation(event);
    }

    public void onEventAsync(PleaseLoadPoisEvent event) {
        loadPois(event);
    }

    public void onEventAsync(PleaseTellIfDbChanges event) {
        bus.post(new ChangesInDB(!poiDao.queryForAllChanges().isEmpty()));
    }

    public void onEventAsync(PleaseLoadPoisToUpdateEvent event) {
        List<Poi> updatedPois = poiDao.queryForAllUpdated();
        List<Poi> newPois = poiDao.queryForAllNew();
        List<Poi> toDeletePois = poiDao.queryToDelete();
        List<PoiNodeRef> wayPoiNodeRef = poiNodeRefDao.queryAllToUpdate();
        List<PoiUpdateWrapper> allPois = new ArrayList<>();

        for (Poi p : updatedPois) {
            allPois.add(new PoiUpdateWrapper(true, p, null, PoiUpdateWrapper.PoiAction.UPDATE));
        }
        for (Poi p : newPois) {
            allPois.add(new PoiUpdateWrapper(true, p, null, PoiUpdateWrapper.PoiAction.CREATE));
        }
        for (Poi p : toDeletePois) {
            allPois.add(new PoiUpdateWrapper(true, p, null, PoiUpdateWrapper.PoiAction.DELETED));
        }
        for (PoiNodeRef p : wayPoiNodeRef) {
            allPois.add(new PoiUpdateWrapper(false, null, p, PoiUpdateWrapper.PoiAction.UPDATE));
        }

        bus.post(new PoisToUpdateLoadedEvent(allPois));
    }

    public void onEventAsync(PleaseLoadPoiTypes event) {
        bus.postSticky(new PoiTypesLoaded(loadPoiTypes()));
    }

    // ********************************
    // ************ public ************
    // ********************************

    /**
     * Method saving a poi and all the associated foreign collections.
     * <p/>
     * Do not call the DAO directly to save a poi, use this method.
     *
     * @param poi The poi to save.
     * @return The saved poi.
     */
    public Poi savePoi(final Poi poi) {
        return databaseHelper.callInTransaction(new Callable<Poi>() {
            @Override
            public Poi call() throws Exception {
                return savePoiNoTransaction(poi);
            }
        });
    }

    /**
     * Method saving a List of POIs and all the associated foreign collections. The saving is done in a transaction.
     * <p/>
     * Do not call the DAO directly to save a List of POIs, use this method.
     *
     * @param pois The List of POIs to save.
     * @return The saved List.
     */
    public List<Poi> savePois(final List<Poi> pois) {
        return databaseHelper.callInTransaction(new Callable<List<Poi>>() {
            @Override
            public List<Poi> call() throws Exception {
                List<Poi> result = new ArrayList<>(pois.size());
                for (Poi poi : pois) {
                    result.add(savePoiNoTransaction(poi));
                }
                return result;
            }
        });
    }

    /**
     * Method saving a List of PoiNodeRefs.
     *
     * @param poiNodeRefs The List of PoiNodeReds to save.
     * @return The saved List.
     */
    public List<PoiNodeRef> savePoiNodeRefs(final List<PoiNodeRef> poiNodeRefs) {
        return databaseHelper.callInTransaction(new Callable<List<PoiNodeRef>>() {
            @Override
            public List<PoiNodeRef> call() throws Exception {
                List<PoiNodeRef> result = new ArrayList<>(poiNodeRefs.size());
                for (PoiNodeRef poiNodeRef : poiNodeRefs) {
                    poiNodeRefDao.createOrUpdate(poiNodeRef);
                    result.add(poiNodeRef);
                }
                return result;
            }
        });
    }

    /**
     * Method saving a poi and all the associated foreign collections without transaction management.
     * <p/>
     * Do not call the DAO directly to save a poi, use this method.
     *
     * @param poi The poi to save
     * @return The saved poi
     * @see #savePoi(Poi)
     */
    private Poi savePoiNoTransaction(Poi poi) {
        List<PoiTag> poiTagsToRemove = poiTagDao.queryByPoiId(poi.getId());
        poiTagsToRemove.removeAll(poi.getTags());
        for (PoiTag poiTag : poiTagsToRemove) {
            poiTagDao.delete(poiTag);
        }

        List<PoiNodeRef> poiNodeRefsToRemove = poiNodeRefDao.queryByPoiId(poi.getId());
        poiNodeRefsToRemove.removeAll(poi.getNodeRefs());
        for (PoiNodeRef poiNodeRef : poiNodeRefsToRemove) {
            poiNodeRefDao.delete(poiNodeRef);
        }

        poiDao.createOrUpdate(poi);

        if (poi.getTags() != null) {
            for (PoiTag poiTag : poi.getTags()) {
                poiTag.setPoi(poi);
                poiTagDao.createOrUpdate(poiTag);
            }
        }

        if (poi.getNodeRefs() != null) {
            for (PoiNodeRef poiNodeRef : poi.getNodeRefs()) {
                poiNodeRef.setPoi(poi);
                poiNodeRefDao.createOrUpdate(poiNodeRef);

            }
        }

        return poi;
    }

    /**
     * Method saving a PoiType and all the associated foreign collections.
     * <p/>
     * Do not call the DAO directly to save a PoiType, use this method.
     *
     * @param poiType The PoiType to save.
     * @return The saved PoiType.
     */
    public PoiType savePoiType(final PoiType poiType) {
        return databaseHelper.callInTransaction(new Callable<PoiType>() {
            @Override
            public PoiType call() throws Exception {
                poiTypeDao.createOrUpdate(poiType);
                List<PoiTypeTag> poiTypeTagsToDelete = poiTypeTagDao.queryByPoiTypeId(poiType.getId());
                poiTypeTagsToDelete.removeAll(poiType.getTags());
                poiTypeTagDao.delete(poiTypeTagsToDelete);
                for (PoiTypeTag poiTypeTag : poiType.getTags()) {
                    poiTypeTag.setPoiType(poiType);
                    poiTypeTagDao.createOrUpdate(poiTypeTag);
                }

                for (KeyWord keyWord : poiType.getKeyWords()) {
                    keyWord.setPoiType(poiType);
                    keyWordDao.createOrUpdate(keyWord);
                }

                return poiType;
            }
        });
    }

    /**
     * Query for a Poi with a given id eagerly.
     *
     * @param id The id of the Poi to load.
     * @return The queried Poi.
     */
    public Poi queryForId(Long id) {
        Poi poi = poiDao.queryForId(id);
        if (poi == null) {
            return null;
        }
        poiTypeDao.refresh(poi.getType());
        poi.setTags(loadLazyForeignCollection(poi.getTags()));
        poi.getType().setTags(loadLazyForeignCollection(poi.getType().getTags()));
        return poi;
    }

    /**
     * Delete a Poi and all PoiTags and PoiNodeRefs associated.
     * <p/>
     * Do not call the DAO directly to delete a Poi, use this method.
     *
     * @param poi The Poi to delete.
     */
    public void deletePoi(final Poi poi) {
        databaseHelper.callInTransaction(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                Poi poiToDelete = poiDao.queryForId(poi.getId());
                if (poiToDelete == null) {
                    return null;
                }
                List<PoiNodeRef> poiNodeRefsToDelete = poiNodeRefDao.queryByPoiId(poiToDelete.getId());
                List<PoiTag> poiTagsToDelete = poiTagDao.queryByPoiId(poiToDelete.getId());

                Timber.d("NodeRefs to delete : %d", poiNodeRefsToDelete.size());
                Timber.d("NodeTags to delete : %d", poiTagsToDelete.size());

                poiTagDao.delete(poiTagsToDelete);
                poiNodeRefDao.delete(poiNodeRefsToDelete);
                poiDao.delete(poiToDelete);

                Timber.i("Deleted Poi %d", poiToDelete.getId());
                return null;
            }
        });
    }

    /**
     * Delete a PoiType and all the PoiTypeTags and POIs associated.
     * <p/>
     * Do not call the DAO directly to delete a PoiType, use this method.
     *
     * @param poiType The PoiType to delete.
     */
    public void deletePoiType(PoiType poiType) {
        final Long id = poiType.getId();
        databaseHelper.callInTransaction(new Callable<PoiType>() {
            @Override
            public PoiType call() throws Exception {
                List<Long> poiIdsToDelete = poiDao.queryAllIdsByPoiTypeId(id);

                Timber.d("PoiTags deleted : %d", poiTagDao.deleteByPoiIds(poiIdsToDelete));
                Timber.d("PoiNodeRefs deleted : %d", poiNodeRefDao.deleteByPoiIds(poiIdsToDelete));
                Timber.d("POIs deleted : %d", poiDao.deleteIds(poiIdsToDelete));

                Timber.d("PoiTypeTags deleted : %d", poiTypeTagDao.deleteByPoiTypeId(id));
                poiTypeDao.deleteById(id);
                Timber.i("Deleted PoiType id=%d", id);
                return null;
            }
        });
    }

    /**
     * Delete all the POIs who are ways and have no PoiType ans the PoiNodeRefs associated.
     * <p/>
     * Do not call the DAO directly to delete a way, use this method.
     */
    public void deleteAllWays() {
        databaseHelper.callInTransaction(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                List<Poi> pois = poiDao.queryForAllWaysNoType();
                List<Poi> poisToDelete = new ArrayList<>();

                for (Poi poi : pois) {
                    boolean delete = true;
                    for (PoiNodeRef poiNodeRef : poi.getNodeRefs()) {
                        if (poiNodeRef.getUpdated()) {
                            delete = false;
                        }
                    }
                    if (delete) {
                        poisToDelete.add(poi);
                        poiNodeRefDao.delete(poi.getNodeRefs());
                    }
                }

                poiDao.delete(poisToDelete);
                return null;
            }
        });
    }

    /**
     * Delete all the POIs who are ways and have no PoiType except the POIs in parameters. Delete also the PoiNodeRefs associated.
     * <p/>
     * Do not call the DAO directly to delete a way, use this method.
     *
     * @param exceptions The POIs who shouldn't be deleted.
     */
    public void deleteAllWaysExcept(final List<Poi> exceptions) {
        databaseHelper.callInTransaction(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                List<Poi> pois = poiDao.queryForAllWaysNoType();
                pois.removeAll(exceptions);
                List<Poi> poisToDelete = new ArrayList<>();

                for (Poi poi : pois) {
                    boolean delete = true;
                    for (PoiNodeRef poiNodeRef : poi.getNodeRefs()) {
                        if (poiNodeRef.getUpdated()) {
                            delete = false;
                        }
                    }
                    if (delete) {
                        poisToDelete.add(poi);
                        poiNodeRefDao.delete(poi.getNodeRefs());
                    }
                }

                poiDao.delete(poisToDelete);
                return null;
            }
        });
    }

    /**
     * Merge POIs in parameters to those already in the database.
     *
     * @param remotePois The POIs to merge.
     */
    public void mergeFromOsmPois(List<Poi> remotePois) {
        List<Poi> toMergePois = new ArrayList<>();

        Map<String, Poi> remotePoisMap = new HashMap<>();
        // Map remote Poi backend Ids
        for (Poi poi : remotePois) {
            remotePoisMap.put(poi.getBackendId(), poi);
        }

        // List matching Pois
        List<Poi> localPois = poiDao.queryForBackendIds(remotePoisMap.keySet());


        Map<String, Poi> localPoisMap = new HashMap<>();
        // Map matching local Pois
        for (Poi localPoi : localPois) {
            localPoisMap.put(localPoi.getBackendId(), localPoi);
        }

        // Browse remote pois
        for (Poi remotePoi : remotePois) {
            Poi localPoi = localPoisMap.get(remotePoi.getBackendId());
            Long localVersion = -1L;
            // If localPoi is versioned
            if (localPoi != null && localPoi.getVersion() != null) {
                localVersion = Long.valueOf(localPoi.getVersion());
            }
            // Compute version delta
            if (Long.valueOf(remotePoi.getVersion()) > localVersion) {
                // Remote version is newer, override existing one
                if (localPoi != null) {
                    remotePoi.setId(localPoi.getId());
                }
                // This Poi should be updated
                toMergePois.add(remotePoi);
            }
        }

        // savePois of either new or existing Pois
        savePois(toMergePois);
    }

    /**
     * Get the date of the last update of POIs.
     *
     * @return The date of the last update.
     */
    public DateTime getLastUpdateDate() {
        return poiDao.queryForMostRecentChangeDate();
    }

    /**
     * Query for all the POIs contained in the bounds defined by the box.
     *
     * @param box Bounds of the search in latitude and longitude coordinates.
     * @return The POIs contained in the box.
     */
    public List<Poi> queryForAllInRect(Box box) {
        return poiDao.queryForAllInRect(box);
    }

    /**
     * Query for all POIs who are ways.
     *
     * @return The list of POIs who are ways.
     */
    public List<Poi> queryForAllWays() {
        return poiDao.queryForAllWays();
    }

    /**
     * Query for all the existing values of a given PoiTag.
     *
     * @param key The key of the PoiTag.
     * @return The list of values.
     */
    public List<String> suggestionsForTagValue(String key) {
        return poiTagDao.existingValuesForTag(key);
    }

    /**
     * Query for all the existing values of each PoiTag of the List.
     *
     * @param keys The list of PoiTags.
     * @return The map of results.
     */
    public Map<String, List<String>> suggestionsForTagsValue(List<String> keys) {
        Map<String, List<String>> res = new HashMap<>();
        for (String key : keys) {
            List<String> temp = suggestionsForTagValue(key);
            res.put(key, temp);
        }
        return res;
    }

    /**
     * Get all the PoiTypes in the database.
     *
     * @return A ID,PoiType map with all the PoiTypes.
     */
    public Map<Long, PoiType> loadPoiTypes() {
        List<PoiType> poiTypes = poiTypeDao.queryForAll();
        Map<Long, PoiType> result = new HashMap<>();
        for (PoiType poiType : poiTypes) {
            result.put(poiType.getId(), poiType);
        }
        return result;
    }

    /**
     * Get the PoiType with the given id.
     *
     * @param id The id of the PoiType.
     * @return The PoiType.
     */
    public PoiType getPoiType(Long id) {
        return poiTypeDao.queryForId(id);
    }

    /**
     * Get all the PoiTypes alphabetically sorted.
     *
     * @return The List of PoiTypes alphabetically sorted.
     */
    public List<PoiType> getPoiTypesSortedByName() {
        return poiTypeDao.queryAllSortedByName();
    }

    /**
     * Check whether the database has been initialized.
     *
     * @return Whether the database has been initialized.
     */
    public boolean isDbInitialized() {
        long count = poiTypeDao.countOf();
        Timber.d("pois in database : %s", count);
        return count > 0;
    }

    /**
     * Update the PoiTypes in the database with the given List of PoiTypes.
     *
     * @param newPoiTypes The PoiTypes to update.
     */
    public void updatePoiTypes(List<PoiType> newPoiTypes) {
        for (PoiType newPoiType : newPoiTypes) {
            PoiType byBackendId = poiTypeDao.findByBackendId(newPoiType.getBackendId());
            if (byBackendId != null) {
                newPoiType.setId(byBackendId.getId());
            }
            savePoiType(newPoiType);
        }
    }

    // *********************************
    // ************ private ************
    // *********************************

    /**
     * Send a {@link io.mapsquare.osmcontributor.core.events.PoiForEditionLoadedEvent} containing the Poi to edit and the suggestions for the PoiTypeTags.
     *
     * @param id The id of the Poi to edit.
     */
    private void loadPoiForEdition(Long id) {
        Poi poi = queryForId(id);
        List<String> keys = new ArrayList<>();

        for (PoiTypeTag poiTypeTag : poi.getType().getTags()) {
            keys.add(poiTypeTag.getKey());
        }

        bus.post(new PoiForEditionLoadedEvent(poi, suggestionsForTagsValue(keys)));
    }

    /**
     * Send a {@link io.mapsquare.osmcontributor.core.events.PoiForEditionLoadedEvent} containing the suggestions for the PoiTypeTags and a new Poi to complete.
     *
     * @param event Event containing the position, level and PoiType of the Poi to create.
     */
    private void loadPoiForCreation(PleaseLoadPoiForCreationEvent event) {
        Poi poi = new Poi();
        Set<Double> level = new HashSet<>();
        level.add(event.getLevel());
        poi.setLevel(level);
        poi.setLatitude(event.getLat());
        poi.setLongitude(event.getLng());
        poi.setType(poiTypeDao.queryForId(event.getPoiType()));
        List<String> keys = new ArrayList<>();

        Map<String, String> defaultTags = new HashMap<>();
        for (PoiTypeTag poiTypeTag : poi.getType().getTags()) {
            if (poiTypeTag.getValue() != null) { // default tags should be set in the corresponding POI
                defaultTags.put(poiTypeTag.getKey(), poiTypeTag.getValue());
            }
        }
        poi.applyChanges(defaultTags);


        for (PoiTypeTag poiTypeTag : poi.getType().getTags()) {
            keys.add(poiTypeTag.getKey());
        }

        bus.post(new PoiForEditionLoadedEvent(poi, suggestionsForTagsValue(keys)));
    }

    /**
     * Send a {@link io.mapsquare.osmcontributor.core.events.PoisLoadedEvent} containing all the POIs
     * in the Box of the {@link io.mapsquare.osmcontributor.core.events.PleaseLoadPoisEvent}.
     *
     * @param event Event containing the box to load.
     */
    private void loadPois(PleaseLoadPoisEvent event) {
        bus.post(new PoisLoadedEvent(event.getBox(), queryForAllInRect(event.getBox())));
    }
}
