package com.corkboard.client;

import com.corkboard.BoardEntity;
import net.minecraft.client.Minecraft;

public class ClientHooks {
    public static void openBoard(BoardEntity board) {
        Minecraft.getInstance().setScreen(new BoardScreen(board.getId()));
    }
}
