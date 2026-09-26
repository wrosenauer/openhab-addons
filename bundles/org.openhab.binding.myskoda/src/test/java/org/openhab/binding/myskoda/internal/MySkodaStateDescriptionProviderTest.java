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
package org.openhab.binding.myskoda.internal;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import java.util.Objects;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.builder.ChannelBuilder;
import org.openhab.core.thing.i18n.ChannelTypeI18nLocalizationService;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.StateDescriptionFragmentBuilder;

/**
 * Tests for {@link MySkodaStateDescriptionProvider}.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@NonNullByDefault
class MySkodaStateDescriptionProviderTest {

    private final MySkodaStateDescriptionProvider provider = new MySkodaStateDescriptionProvider(
            mock(EventPublisher.class), mock(ItemChannelLinkRegistry.class),
            mock(ChannelTypeI18nLocalizationService.class));
    private final Channel channel = ChannelBuilder
            .create(new ChannelUID("myskoda:vehicle:home:mycar:charging#target-state-of-charge"), "Number").build();
    private final StateDescription original = Objects.requireNonNull(StateDescriptionFragmentBuilder.create()
            .withPattern("%.0f %unit%").withReadOnly(false).build().toStateDescription());

    @Test
    void makesChannelReadOnlyAndKeepsTheRest() {
        provider.setReadOnly(channel.getUID(), true);

        StateDescription description = Objects.requireNonNull(provider.getStateDescription(channel, original, null));
        assertThat(description.isReadOnly(), is(true));
        assertThat(description.getPattern(), is("%.0f %unit%"));
    }

    @Test
    void leavesChannelWritableAgain() {
        provider.setReadOnly(channel.getUID(), true);
        provider.setReadOnly(channel.getUID(), false);

        StateDescription description = provider.getStateDescription(channel, original, null);
        assertThat(description == null || !description.isReadOnly(), is(true));
    }
}
