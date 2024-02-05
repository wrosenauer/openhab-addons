/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
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
package org.openhab.binding.connectedcar.internal.api;

import org.eclipse.jdt.annotation.NonNullByDefault;

/**
 * The {@link ApiEventListener} defines the callback interface from the ApiBase class
 *
 * @author Markus Michels - Initial contribution
 * @author Thomas Knaller - Maintainer
 * @author Dr. Yves Kreis - Maintainer
 */
@NonNullByDefault
public interface ApiEventListener {

    void onActionSent(String service, String action, String requestId);

    void onActionTimeout(String service, String action, String requestId);

    void onActionResult(String service, String action, String requestId, String status, String statusDetail);

    void onActionNotification(String service, String action, String message);

    void onRateLimit(int rateLimit);
}
