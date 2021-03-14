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
package org.openhab.binding.groupepsa.internal.rest.api.dto;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

/**
 * @author Arjan Mels - Initial contribution
 */
@NonNullByDefault
public class Properties {

    private @Nullable Double heading;
    private @Nullable Double signalQuality;
    private @Nullable String type;

    public @Nullable Double getHeading() {
        return heading;
    }

    public @Nullable Double getSignalQuality() {
        return signalQuality;
    }

    public @Nullable String getType() {
        return type;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this).append("heading", heading).append("signalQuality", signalQuality)
                .append("type", type).toString();
    }
}
