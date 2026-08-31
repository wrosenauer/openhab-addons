/*
 * Copyright (c) 2010-2025 Contributors to the openHAB project
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
 * The {@link ProblemDetail} dto is the RFC 9457 {@code application/problem+json} error body
 * returned by the MySkoda public API on 4xx/5xx responses.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
public class ProblemDetail {

    public String type = "";
    public String title = "";
    public int status;
    public String detail = "";
    public String instance = "";
}
