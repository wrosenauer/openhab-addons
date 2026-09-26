/*
 * Copyright (c) 2010-2026 Contributors to the openHAB project
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
package org.openhab.binding.myskoda.internal.api.dto;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ChargingLimit} dto is the request body of {@code PUT /charging/limit}.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class ChargingLimit {

    public int targetStateOfChargeInPercent;

    public ChargingLimit(int targetStateOfChargeInPercent) {
        this.targetStateOfChargeInPercent = targetStateOfChargeInPercent;
    }
}
