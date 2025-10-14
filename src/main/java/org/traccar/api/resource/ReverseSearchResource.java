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
package org.traccar.api.resource;

import jakarta.inject.Inject;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.api.BaseResource;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.helper.LogAction;
import org.traccar.model.UserRestrictions;
import org.traccar.reports.ReverseSearchReportProvider;
import org.traccar.reports.model.ReverseSearchItem;
import org.traccar.storage.StorageException;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Date;
import java.util.List;

@Path("reversesearch")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReverseSearchResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReverseSearchResource.class);

    @Inject
    private ReverseSearchReportProvider reverseSearchReportProvider;

    @Inject
    private LogAction actionLogger;

    @Inject
    private Config config;

    @Inject
    private Client client;

    @Context
    private HttpServletRequest request;

    /**
     * Geocode an address to coordinates using Nominatim (OpenStreetMap)
     * Uses the same geocoder configuration as Traccar
     * 
     * @param address Address to geocode
     * @return JSON with latitude and longitude
     */
    @Path("geocode")
    @GET
    public GeocodingResult geocode(@QueryParam("address") String address) throws StorageException {
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        
        GeocodingResult result = new GeocodingResult();
        result.setAddress(address);
        
        // Check if geocoder is enabled in Traccar configuration
        if (!config.hasKey(Keys.GEOCODER_ENABLE) || !config.getBoolean(Keys.GEOCODER_ENABLE)) {
            result.setSuccess(false);
            result.setMessage("Geocoder is not enabled in Traccar configuration. Please enter coordinates manually.");
            return result;
        }
        
        try {
            // Use Nominatim for forward geocoding (address -> coordinates)
            String geocoderUrl = config.getString(Keys.GEOCODER_URL);
            if (geocoderUrl == null) {
                geocoderUrl = "https://nominatim.openstreetmap.org";
            }
            
            String searchUrl = geocoderUrl + "/search?q=" + URLEncoder.encode(address, "UTF-8") 
                    + "&format=json&limit=1";
            
            // Add API key if configured
            String key = config.getString(Keys.GEOCODER_KEY);
            if (key != null) {
                searchUrl += "&key=" + key;
            }
            
            // Add language if configured
            String language = config.getString(Keys.GEOCODER_LANGUAGE);
            if (language != null) {
                searchUrl += "&accept-language=" + language;
            }
            
            LOGGER.debug("Forward geocoding request: {}", searchUrl);
            
            // Make HTTP request
            JsonArray jsonArray = client.target(searchUrl)
                    .request()
                    .header("User-Agent", "Traccar")
                    .get(JsonArray.class);
            
            if (jsonArray != null && !jsonArray.isEmpty()) {
                JsonObject location = jsonArray.getJsonObject(0);
                
                double lat = Double.parseDouble(location.getString("lat"));
                double lon = Double.parseDouble(location.getString("lon"));
                
                result.setLatitude(lat);
                result.setLongitude(lon);
                result.setSuccess(true);
                result.setMessage(location.containsKey("display_name") 
                        ? location.getString("display_name") 
                        : "Location found");
                
                LOGGER.info("Geocoded address '{}' to coordinates: {}, {}", address, lat, lon);
            } else {
                result.setSuccess(false);
                result.setMessage("Address not found. Please check the address or enter coordinates manually.");
            }
            
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("URL encoding error", e);
            result.setSuccess(false);
            result.setMessage("Invalid address format");
        } catch (Exception e) {
            LOGGER.error("Geocoding failed", e);
            result.setSuccess(false);
            result.setMessage("Geocoding service unavailable: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Perform reverse search to find devices that were in a specific area during a time period
     * 
     * @param deviceIds Device IDs to filter (optional)
     * @param groupIds Group IDs to filter (optional)
     * @param latitude Center latitude
     * @param longitude Center longitude
     * @param radius Radius in meters
     * @param from Start date/time
     * @param to End date/time
     * @return Collection of reverse search results
     * @throws StorageException if storage error occurs
     */
    @GET
    public Collection<ReverseSearchItem> get(
            @QueryParam("deviceId") List<Long> deviceIds,
            @QueryParam("groupId") List<Long> groupIds,
            @QueryParam("latitude") double latitude,
            @QueryParam("longitude") double longitude,
            @QueryParam("radius") double radius,
            @QueryParam("from") Date from,
            @QueryParam("to") Date to) throws StorageException {
        
        permissionsService.checkRestriction(getUserId(), UserRestrictions::getDisableReports);
        
        actionLogger.report(
                request,
                getUserId(),
                false,
                "reversesearch",
                from,
                to,
                deviceIds,
                groupIds
        );
        
        return reverseSearchReportProvider.getObjects(
                getUserId(),
                deviceIds,
                groupIds,
                latitude,
                longitude,
                radius,
                from,
                to
        );
    }

    /**
     * Inner class for geocoding results
     */
    public static class GeocodingResult {
        private String address;
        private double latitude;
        private double longitude;
        private boolean success;
        private String message;

        public String getAddress() {
            return address;
        }

        public void setAddress(String address) {
            this.address = address;
        }

        public double getLatitude() {
            return latitude;
        }

        public void setLatitude(double latitude) {
            this.latitude = latitude;
        }

        public double getLongitude() {
            return longitude;
        }

        public void setLongitude(double longitude) {
            this.longitude = longitude;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

