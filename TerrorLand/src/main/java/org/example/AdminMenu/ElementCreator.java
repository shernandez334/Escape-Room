package org.example.AdminMenu;

import org.example.entities.Clue;
import org.example.entities.Decoration;
import org.example.entities.Room;
import org.example.factory.DatabaseInputFactorySQL;
import org.example.factory.ElementFactoryCreatorMySql;
import org.example.factory.RoomFactoryCreator;
import org.example.util.IOHelper;
import org.example.util.MenuHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElementCreator {
    private static final Logger LOGGER = LoggerFactory.getLogger(RoomManager.class);
    private static final ElementFactoryCreatorMySql ELEMCREATOR = new ElementFactoryCreatorMySql();
    private static final RoomFactoryCreator ROOMCREATOR = new RoomFactoryCreator();
    private static final DatabaseInputFactorySQL DATABASEINPUT = new DatabaseInputFactorySQL();


    public void createRoomHasElements() {
        int op;
        Room room = ROOMCREATOR.createElementRoom();
        do {
            op = MenuHelper.readSelection("Element which will be inputted into the room: ",
                    ">", "1. Decoration", "2. Clue", "3. Exit");

            switch (op) {
                case 1 -> addDecorationToRoomHasElement(room);
                case 2 -> addClueToRoomHasElement(room);
                case 3 -> LOGGER.info("Returning to the Room Manager Menu.");
            }
        } while (op != 3);
        room.reset();
    }

    public void addDecorationToRoomHasElement(Room room){
        Decoration decor = ELEMCREATOR.createDecoration();
        DATABASEINPUT.inputDecorationIntoTable(decor.getElementId(), decor.getMaterial());
        int quantity = IOHelper.readInt("Quantity: ");
        DATABASEINPUT.inputRoomHasElementIntoTable(room.getRoomId(), decor.getElementId(), quantity);
    }
    public void addClueToRoomHasElement(Room room){
        Clue clue = ELEMCREATOR.createClue();
        DATABASEINPUT.inputClueIntoTable(clue.getElementId(), clue.getTheme());
        int quantity = IOHelper.readInt("Quantity: ");
        DATABASEINPUT.inputRoomHasElementIntoTable(room.getRoomId(), clue.getElementId(), quantity);
    }
}
