package me.jadenp.notbounties.data;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import me.jadenp.notbounties.utils.SerializeInventory;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SetterTypeAdapter extends TypeAdapter<Setter> {
    @Override
    public void write(JsonWriter writer, Setter setter) throws IOException {
        if (setter == null) {
            writer.nullValue();
            return;
        }

        writer.beginObject();
        writer.name("name").value(setter.getName());
        writer.name("uuid").value(setter.getUuid().toString());
        writer.name("amount").value(setter.getAmount());
        writer.name("items");
        if (setter.getItems().isEmpty()) {
            writer.nullValue();
        } else {
            writer.value(SerializeInventory.itemStackArrayToBase64(setter.getItems().toArray(new ItemStack[0])));
        }

        writer.name("time").value(setter.getTimeCreated());
        writer.name("playtime").value(setter.getReceiverPlaytime());
        writer.name("notified").value(setter.isNotified());
        writer.name("display").value(setter.getDisplayAmount());
        writer.name("whitelist");
        new WhitelistTypeAdapter().write(writer, setter.getWhitelist());
        writer.endObject();
    }

    @Override
    public Setter read(JsonReader reader) throws IOException {
        if (reader.peek() == JsonToken.NULL) {
            reader.nextNull();
            return null;
        }

        String playerName = null;
        UUID uuid = null;
        double amount = 0;
        List<ItemStack> itemStacks = new ArrayList<>();
        long time = 0;
        long playtime = 0;
        boolean notified = false;
        double display = 0;
        Whitelist whitelist = null;

        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case "name" -> playerName = reader.nextString();
                case "uuid" -> uuid = UUID.fromString(reader.nextString());
                case "amount" -> amount = reader.nextDouble();
                case "items" -> {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull();
                    } else {
                        itemStacks.addAll(List.of(SerializeInventory.itemStackArrayFromBase64(reader.nextString())));
                    }
                }
                case "time" -> time = reader.nextLong();
                case "playtime" -> playtime = reader.nextLong();
                case "notified" -> notified = reader.nextBoolean();
                case "display" -> display = reader.nextDouble();
                case "whitelist" -> whitelist = new WhitelistTypeAdapter().read(reader);
                default -> reader.skipValue();
            }
        }
        reader.endObject();

        return new Setter(playerName, uuid, amount, itemStacks, time, notified, whitelist, playtime, display);
    }

}
