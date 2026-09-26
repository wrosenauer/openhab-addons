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
import org.eclipse.jdt.annotation.Nullable;

import com.google.gson.JsonElement;

/**
 * The {@link ProblemDetail} dto is the RFC 9457 {@code application/problem+json} error body
 * returned by the MySkoda public API on 4xx/5xx responses. When a request parameter is rejected
 * ({@code 400}), the extension members {@code parameter}, {@code rejectedValue} and
 * {@code allowedValues} identify what to fix.
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
    public String parameter = "";
    public @Nullable JsonElement rejectedValue;
    public @Nullable JsonElement allowedValues;

    /**
     * @return a message describing this problem, falling back to {@code fallback} when the
     *         problem carries neither a detail nor a title
     */
    public String toMessage(String fallback) {
        String message = !detail.isBlank() ? detail : !title.isBlank() ? title : fallback;
        JsonElement allowed = allowedValues;
        if (!parameter.isBlank() && allowed != null) {
            message += " (" + parameter + " must be one of " + allowed + ")";
        }
        return message;
    }
}
