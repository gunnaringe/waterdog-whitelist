package xyz.haxxor.waterdogaccess;

import dev.waterdog.waterdogpe.network.protocol.handler.PluginPacketHandler;
import dev.waterdog.waterdogpe.network.serverinfo.ServerInfo;
import dev.waterdog.waterdogpe.player.ProxiedPlayer;
import org.cloudburstmc.protocol.bedrock.PacketDirection;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormRequestPacket;
import org.cloudburstmc.protocol.bedrock.packet.ModalFormResponsePacket;
import org.cloudburstmc.protocol.common.PacketSignal;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Sends the lobby's world-select menu as a native Bedrock simple form (a client-rendered button
 * list, not an in-world UI) and consumes the client's response before it would otherwise be
 * forwarded to the lobby's own backend server, which has no idea what a ModalFormResponsePacket
 * with our formId means. One instance lives for a player's whole session (registered once in
 * WaterdogAccess.onLogin) since WaterdogPE has no API to remove a single PluginPacketHandler
 * later - re-sending the menu just updates this instance's expected formId/options instead of
 * registering a second handler.
 */
public class MenuHandler implements PluginPacketHandler {

    private final ProxiedPlayer player;
    private final AccessDatabase database;

    private volatile int activeFormId = -1;
    private volatile List<ServerInfo> activeOptions = List.of();

    public MenuHandler(ProxiedPlayer player, AccessDatabase database) {
        this.player = player;
        this.database = database;
    }

    public void sendMenu() {
        List<ServerInfo> options = this.player.getProxy().getServers().stream()
                .filter(server -> !server.getServerName().equals("lobby"))
                .filter(this::isAllowed)
                .sorted(Comparator.comparing(ServerInfo::getServerName))
                .collect(Collectors.toList());

        int formId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        this.activeOptions = options;
        this.activeFormId = formId;

        String buttons = options.stream()
                .map(server -> "{\"text\":\"" + escape(server.getServerName()) + "\"}")
                .collect(Collectors.joining(","));
        String formData = "{\"type\":\"form\",\"title\":\"SortCraft\",\"content\":\"Choose a world\",\"buttons\":[" + buttons + "]}";

        ModalFormRequestPacket packet = new ModalFormRequestPacket();
        packet.setFormId(formId);
        packet.setFormData(formData);
        this.player.sendPacket(packet);
    }

    @Override
    public PacketSignal handlePacket(BedrockPacket packet, PacketDirection direction) {
        if (direction != PacketDirection.SERVER_BOUND || !(packet instanceof ModalFormResponsePacket)) {
            return PacketSignal.UNHANDLED;
        }

        ModalFormResponsePacket response = (ModalFormResponsePacket) packet;
        if (response.getFormId() != this.activeFormId) {
            return PacketSignal.UNHANDLED;
        }
        this.activeFormId = -1;

        String formData = response.getFormData();
        if (formData != null) {
            try {
                int index = Integer.parseInt(formData.trim());
                List<ServerInfo> options = this.activeOptions;
                if (index >= 0 && index < options.size()) {
                    this.player.connect(options.get(index));
                }
            } catch (NumberFormatException ignored) {
                // Player closed the form or sent something unexpected - just stay in lobby.
            }
        }
        return PacketSignal.HANDLED;
    }

    private boolean isAllowed(ServerInfo server) {
        try {
            return this.database.isAllowed(this.player.getXuid(), server.getServerName());
        } catch (SQLException e) {
            return false;
        }
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
