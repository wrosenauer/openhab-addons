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

import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.thing.Channel;
import org.openhab.core.thing.ChannelUID;
import org.openhab.core.thing.binding.BaseDynamicStateDescriptionProvider;
import org.openhab.core.thing.i18n.ChannelTypeI18nLocalizationService;
import org.openhab.core.thing.link.ItemChannelLinkRegistry;
import org.openhab.core.thing.type.DynamicStateDescriptionProvider;
import org.openhab.core.types.StateDescription;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

/**
 * The {@link MySkodaStateDescriptionProvider} adapts channel state descriptions to what a vehicle
 * reports: it narrows state options (e.g. to the charge modes a vehicle accepts) and makes
 * channels read-only whose operation the vehicle does not support.
 *
 * @author Wolfgang Rosenauer - Initial contribution
 */
@Component(service = { DynamicStateDescriptionProvider.class, MySkodaStateDescriptionProvider.class })
@NonNullByDefault
public class MySkodaStateDescriptionProvider extends BaseDynamicStateDescriptionProvider {

    private final Set<ChannelUID> readOnlyChannels = ConcurrentHashMap.newKeySet();

    @Activate
    public MySkodaStateDescriptionProvider(final @Reference EventPublisher eventPublisher, //
            final @Reference ItemChannelLinkRegistry itemChannelLinkRegistry, //
            final @Reference ChannelTypeI18nLocalizationService channelTypeI18nLocalizationService) {
        this.eventPublisher = eventPublisher;
        this.itemChannelLinkRegistry = itemChannelLinkRegistry;
        this.channelTypeI18nLocalizationService = channelTypeI18nLocalizationService;
    }

    public void setReadOnly(ChannelUID channelUID, boolean readOnly) {
        if (readOnly) {
            readOnlyChannels.add(channelUID);
        } else {
            readOnlyChannels.remove(channelUID);
        }
    }

    @Override
    public @Nullable StateDescription getStateDescription(Channel channel, @Nullable StateDescription original,
            @Nullable Locale locale) {
        StateDescription description = super.getStateDescription(channel, original, locale);
        if (!readOnlyChannels.contains(channel.getUID())) {
            return description;
        }
        StateDescription base = description != null ? description : original;
        StateDescriptionFragmentBuilder builder = base != null ? StateDescriptionFragmentBuilder.create(base)
                : StateDescriptionFragmentBuilder.create();
        return builder.withReadOnly(true).build().toStateDescription();
    }
}
