/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.binding.groupepsa.internal.rest.api;

import static org.openhab.binding.groupepsa.internal.GroupePSABindingConstants.API_URL;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.openhab.binding.groupepsa.internal.bridge.GroupePSABridgeHandler;
import org.openhab.binding.groupepsa.internal.rest.api.dto.ErrorObject;
import org.openhab.binding.groupepsa.internal.rest.api.dto.User;
import org.openhab.binding.groupepsa.internal.rest.api.dto.Vehicle;
import org.openhab.binding.groupepsa.internal.rest.api.dto.VehicleStatus;
import org.openhab.binding.groupepsa.internal.rest.exceptions.GroupePSACommunicationException;
import org.openhab.binding.groupepsa.internal.rest.exceptions.UnauthorizedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.filosganga.geogson.gson.GeometryAdapterFactory;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;

/**
 * Allows access to the GroupePSAConnectApi
 *
 * @author Arjan Mels - Initial contribution
 */
@NonNullByDefault
public class GroupePSAConnectApi {
    private static final Logger logger = LoggerFactory.getLogger(GroupePSAConnectApi.class);

    private final HttpClient httpClient;
    private final GroupePSABridgeHandler bridge;
    private final String clientId;
    private final String realm;

    protected final Gson gson;

    public GroupePSAConnectApi(HttpClient httpClient, GroupePSABridgeHandler bridge, String clientId, String realm) {
        this.httpClient = httpClient;
        this.bridge = bridge;
        this.clientId = clientId;
        this.realm = realm;

        gson = new GsonBuilder().registerTypeAdapterFactory(new GeometryAdapterFactory())
                .registerTypeAdapter(ZonedDateTime.class, new JsonDeserializer<ZonedDateTime>() {
                    @Override
                    public @Nullable ZonedDateTime deserialize(JsonElement json, Type typeOfT,
                            JsonDeserializationContext context) throws JsonParseException {
                        return ZonedDateTime.parse(json.getAsJsonPrimitive().getAsString());
                    }
                }).registerTypeAdapter(Duration.class, new JsonDeserializer<Duration>() {
                    @Override
                    public @Nullable Duration deserialize(JsonElement json, Type typeOfT,
                            JsonDeserializationContext context) throws JsonParseException {
                        return Duration.parse(json.getAsJsonPrimitive().getAsString());
                    }
                }).create();
    }

    protected HttpClient getHttpClient() {
        return httpClient;
    }

    protected GroupePSABridgeHandler getBridge() {
        return bridge;
    }

    public String getBaseUrl() {
        return API_URL;
    }

    public static String getResourceFileAsString(String fileName) throws GroupePSACommunicationException {
        try (InputStream is = GroupePSAConnectApi.class.getResourceAsStream(fileName)) {
            try (InputStreamReader isr = new InputStreamReader(is); BufferedReader reader = new BufferedReader(isr)) {
                return reader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        } catch (Exception e) {
            throw new GroupePSACommunicationException(e);
        }
    }

    private ContentResponse executeRequest(final String uri) throws GroupePSACommunicationException {
        return executeRequest(uri, "application/hal+json");
    }

    public ContentResponse executeRequest(final String uri, final String accept)
            throws GroupePSACommunicationException {

        Request request = getHttpClient().newRequest(uri);

        String token = getBridge().authenticate();

        request.timeout(10, TimeUnit.SECONDS);

        request.param("client_id", this.clientId);

        request.header("Authorization", "Bearer " + token);
        request.header("Accept", accept);
        request.header("x-introspect-realm", this.realm);

        request.method(HttpMethod.GET);

        logger.trace("HttpRequest {}", request.getURI());
        logger.trace("HttpRequest Headers:\n{}", request.getHeaders());

        try {
            ContentResponse response = request.send();
            logger.trace("HttpResponse {}", response);
            logger.trace("HttpResponse Headers:\n{}", response.getHeaders());
            logger.trace("HttpResponse Content: {}", response.getContentAsString());
            return response;
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            throw new GroupePSACommunicationException(e);
        }
    }

    private void checkForError(ContentResponse response, int statusCode) throws GroupePSACommunicationException {
        if (statusCode >= 200 && statusCode < 300) {
            return;
        }

        switch (statusCode) {
            case HttpStatus.NOT_FOUND_404:
                ErrorObject error = gson.fromJson(response.getContentAsString(), ErrorObject.class);
                String message = (error != null) ? error.getMessage() : null;
                if (message == null)
                    message = "Unknown";
                throw new GroupePSACommunicationException(statusCode, message);

            case HttpStatus.FORBIDDEN_403:
            case HttpStatus.UNAUTHORIZED_401:
                throw new UnauthorizedException(statusCode, response.getContentAsString());

            default:
                throw new GroupePSACommunicationException(statusCode, response.getContentAsString());
        }
    }

    private <T> @Nullable T parseResponse(ContentResponse response, Class<T> type)
            throws GroupePSACommunicationException {
        int statusCode = response.getStatus();

        checkForError(response, statusCode);

        try {
            return gson.fromJson(response.getContentAsString(), type);
        } catch (JsonSyntaxException e) {
            throw new GroupePSACommunicationException(e);
        }
    }

    public @Nullable List<Vehicle> getVehicles() throws GroupePSACommunicationException {
        ContentResponse response = executeRequest(getBaseUrl() + "/user");

        User user = parseResponse(response, User.class);

        if (user != null) {
            final List<Vehicle> vehicles = user.getVehicles();
            if (vehicles != null)
                return vehicles;
        }

        return null;
    }

    public @Nullable VehicleStatus getVehicleStatus(String vin) throws GroupePSACommunicationException {
        ContentResponse response_odometer = executeRequest(
                getBaseUrl() + "/vehicle/" + vin + "/status?extension=odometer");
        VehicleStatus status_odometer = parseResponse(response_odometer, VehicleStatus.class);

        ContentResponse response = executeRequest(getBaseUrl() + "/vehicle/" + vin + "/status?extension=kinetic");
        VehicleStatus status = parseResponse(response, VehicleStatus.class);
        if (status != null && status_odometer != null)
            status.setOdemeter(status_odometer.getOdemeter());
        else
            status = status_odometer;

        return status;
    }
}
