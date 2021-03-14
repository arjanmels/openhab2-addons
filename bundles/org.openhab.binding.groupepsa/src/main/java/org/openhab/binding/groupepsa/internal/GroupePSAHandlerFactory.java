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
package org.openhab.binding.groupepsa.internal;

import static org.openhab.binding.groupepsa.internal.GroupePSABindingConstants.*;

import java.util.Hashtable;
import java.util.Set;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.eclipse.jetty.client.HttpClient;
import org.openhab.binding.groupepsa.internal.bridge.GroupePSABridgeHandler;
import org.openhab.binding.groupepsa.internal.discovery.GroupePSADiscoveryService;
import org.openhab.binding.groupepsa.internal.things.GroupePSAHandler;
import org.openhab.core.auth.client.oauth2.OAuthFactory;
import org.openhab.core.config.discovery.DiscoveryService;
import org.openhab.core.io.net.http.HttpClientFactory;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingTypeUID;
import org.openhab.core.thing.binding.BaseThingHandlerFactory;
import org.openhab.core.thing.binding.ThingHandler;
import org.openhab.core.thing.binding.ThingHandlerFactory;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link GroupePSAHandlerFactory} is responsible for creating things and
 * thing handlers.
 *
 * @author Arjan Mels - Initial contribution
 */
@NonNullByDefault
@Component(configurationPid = "binding.groupepsa", service = ThingHandlerFactory.class)
public class GroupePSAHandlerFactory extends BaseThingHandlerFactory {

    private static final Set<ThingTypeUID> SUPPORTED_THING_TYPES_UIDS = Set.of(THING_TYPE_BRIDGE, THING_TYPE_VEHICLE);

    private final OAuthFactory oAuthFactory;
    protected final @NonNullByDefault({}) HttpClient httpClient;
    private @Nullable ServiceRegistration<?> groupePSADiscoveryServiceRegistration;

    @Activate
    public GroupePSAHandlerFactory(@Reference OAuthFactory oAuthFactory,
            @Reference HttpClientFactory httpClientFactory) {
        this.oAuthFactory = oAuthFactory;
        this.httpClient = httpClientFactory.getCommonHttpClient();
    }

    @Override
    public boolean supportsThingType(ThingTypeUID thingTypeUID) {
        return SUPPORTED_THING_TYPES_UIDS.contains(thingTypeUID);
    }

    @Override
    protected @Nullable ThingHandler createHandler(Thing thing) {
        ThingTypeUID thingTypeUID = thing.getThingTypeUID();

        if (THING_TYPE_VEHICLE.equals(thingTypeUID)) {
            return new GroupePSAHandler(thing);
        } else if (THING_TYPE_BRIDGE.equals(thingTypeUID)) {
            GroupePSABridgeHandler handler = new GroupePSABridgeHandler((Bridge) thing, oAuthFactory, httpClient);
            registerGroupePSADiscoveryService(handler);
            return handler;
        }

        return null;
    }

    @Override
    protected synchronized void removeHandler(ThingHandler thingHandler) {
        if (thingHandler instanceof GroupePSABridgeHandler) {
            ServiceRegistration<?> serviceRegistration = groupePSADiscoveryServiceRegistration;
            if (serviceRegistration != null) {
                serviceRegistration.unregister();
                groupePSADiscoveryServiceRegistration = null;
            }
        }
    }

    private void registerGroupePSADiscoveryService(GroupePSABridgeHandler handler) {
        GroupePSADiscoveryService discoveryService = new GroupePSADiscoveryService(handler);
        groupePSADiscoveryServiceRegistration = bundleContext.registerService(DiscoveryService.class.getName(),
                discoveryService, new Hashtable<>());
    }
}
