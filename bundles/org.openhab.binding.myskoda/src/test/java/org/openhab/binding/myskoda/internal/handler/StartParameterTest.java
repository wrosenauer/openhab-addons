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
package org.openhab.binding.myskoda.internal.handler;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StartParameter}.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
class StartParameterTest {

    @Test
    void usesDefaultUntilVehicleReports() {
        StartParameter<Integer> parameter = new StartParameter<>(600);

        assertThat(parameter.get(), is(600));
        assertThat(parameter.updateFromVehicle(900), is(900));
        assertThat(parameter.get(), is(900));
    }

    @Test
    void pendingValueSurvivesPollUntilSent() {
        StartParameter<Integer> parameter = new StartParameter<>(600);
        parameter.updateFromVehicle(900);

        parameter.set(1200);
        // a poll before the start command must not revert the user's value
        assertThat(parameter.updateFromVehicle(900), is(1200));
        assertThat(parameter.get(), is(1200));

        parameter.sent();
        assertThat(parameter.get(), is(1200));
        // once sent, the vehicle's reported value is shown again
        assertThat(parameter.updateFromVehicle(1200), is(1200));
        parameter.updateFromVehicle(300);
        assertThat(parameter.get(), is(300));
    }
}
