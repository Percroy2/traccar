/*
 * Copyright 2025 Ormpt Engineering
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.reports;

import jakarta.inject.Inject;
import org.traccar.config.Config;
import org.traccar.helper.model.DeviceUtil;
import org.traccar.helper.model.PositionUtil;
import org.traccar.model.Device;
import org.traccar.model.Group;
import org.traccar.model.Position;
import org.traccar.reports.common.ReportUtils;
import org.traccar.reports.model.ReverseSearchItem;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Request;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ReverseSearchReportProvider {

    private final ReportUtils reportUtils;
    private final Storage storage;

    @Inject
    public ReverseSearchReportProvider(ReportUtils reportUtils, Storage storage) {
        this.reportUtils = reportUtils;
        this.storage = storage;
    }

    /**
     * Calculate distance between two points using Haversine formula
     * @param lat1 Latitude of first point
     * @param lon1 Longitude of first point
     * @param lat2 Latitude of second point
     * @param lon2 Longitude of second point
     * @return Distance in meters
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final int EARTH_RADIUS = 6371000; // meters
        
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS * c;
    }

    /**
     * Perform reverse geocoding search
     * @param userId User performing the search
     * @param deviceIds Device IDs to filter
     * @param groupIds Group IDs to filter
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radius Radius in meters
     * @param from Start date/time
     * @param to End date/time
     * @return Collection of reverse search items
     * @throws StorageException if storage error occurs
     */
    public Collection<ReverseSearchItem> getObjects(
            long userId,
            Collection<Long> deviceIds,
            Collection<Long> groupIds,
            double latitude,
            double longitude,
            double radius,
            Date from,
            Date to) throws StorageException {
        
        reportUtils.checkPeriodLimit(from, to);

        // Get accessible devices based on user permissions
        Collection<Device> devices = DeviceUtil.getAccessibleDevices(storage, userId, deviceIds, groupIds);
        
        // Get all groups for name mapping
        Map<Long, String> groupNames = storage.getObjects(Group.class, new Request(new Columns.All()))
                .stream()
                .collect(Collectors.toMap(Group::getId, Group::getName));

        List<ReverseSearchItem> results = new ArrayList<>();
        
        for (Device device : devices) {
            // Get all positions for this device in the time range
            Collection<Position> positions = PositionUtil.getPositions(storage, device.getId(), from, to);
            
            // Track entries and exits
            Map<Long, ReverseSearchItem> deviceResults = new HashMap<>();
            Position lastPositionInZone = null;
            
            for (Position position : positions) {
                double distance = calculateDistance(
                        latitude, longitude,
                        position.getLatitude(), position.getLongitude()
                );
                
                boolean isInZone = distance <= radius;
                
                if (isInZone) {
                    if (lastPositionInZone == null) {
                        // Entry point
                        ReverseSearchItem item = new ReverseSearchItem();
                        item.setDeviceId(device.getId());
                        item.setDeviceName(device.getName());
                        item.setGroupId(device.getGroupId());
                        item.setGroupName(groupNames.getOrDefault(device.getGroupId(), ""));
                        item.setEntryTime(position.getFixTime());
                               item.setLatitude(position.getLatitude());
                               item.setLongitude(position.getLongitude());
                               item.setPositionId(position.getId());
                               item.setDistanceFromCenter(distance);
                               item.setLicensePlate(device.getLicensePlate());
                        deviceResults.put(position.getId(), item);
                    }
                    lastPositionInZone = position;
                } else {
                    if (lastPositionInZone != null) {
                        // Exit point - update the last entry with exit time
                        ReverseSearchItem item = deviceResults.get(lastPositionInZone.getId());
                        if (item != null) {
                            item.setExitTime(position.getFixTime());
                        }
                        lastPositionInZone = null;
                    }
                }
            }
            
            // If still in zone at end of period, set exit time to null or end time
            if (lastPositionInZone != null) {
                ReverseSearchItem item = deviceResults.get(lastPositionInZone.getId());
                if (item != null && item.getExitTime() == null) {
                    item.setExitTime(to);
                }
            }
            
            results.addAll(deviceResults.values());
        }
        
        // Sort by entry time
        results.sort((a, b) -> a.getEntryTime().compareTo(b.getEntryTime()));
        
        return results;
    }
}

