package top.yourzi.dialog.model;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;

/**
 * 自定义反序列化器/序列化器：允许 JSON 在动画配置中使用通用简写 "from"/"to"，
 * 由 type 字段决定它们映射到哪一组属性（scale / alpha / rotation）。
 * <p>
 * 同时仍然支持类型专属字段名（fromScale / toScale / fromAlpha / ...）。
 * <p>
 * 注意：不能调用 context.deserialize(json, PortraitAnimationData.class)，
 * 否则会递归调用自身导致栈溢出。这里手动解析所有字段。
 * <p>
 * 序列化时：fromAlpha / toAlpha 的默认值是 {@link Float#NaN}（表示"未指定"），
 * Gson 默认遇到 NaN/Infinity 会抛异常，导致服务端同步对话 JSON 时失败
 * （"NaN is not a valid double value" → 玩家登录时断开：无效的玩家数据）。
 * 这里在写出时跳过非有限值（NaN/Infinity），保证同步链路稳定。
 */
public class PortraitAnimationDataDeserializer
        implements JsonDeserializer<PortraitAnimationData>, JsonSerializer<PortraitAnimationData> {

    @Override
    public PortraitAnimationData deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
            throws JsonParseException {
        if (json == null || json.isJsonNull()) {
            return null;
        }
        if (!json.isJsonObject()) {
            throw new JsonParseException("PortraitAnimationData must be a JSON object");
        }

        JsonObject obj = json.getAsJsonObject();
        PortraitAnimationData data = new PortraitAnimationData();

        // type
        if (obj.has("type") && !obj.get("type").isJsonNull()) {
            data.setType(obj.get("type").getAsString());
        }
        String type = data.getType() != null ? data.getType().toLowerCase() : "";

        // move 字段
        data.setFromX(getFloat(obj, "fromX", 0f));
        data.setFromY(getFloat(obj, "fromY", 0f));
        data.setToX(getFloat(obj, "toX", 0f));
        data.setToY(getFloat(obj, "toY", 0f));

        // scale 字段：优先类型专属名 fromScale/toScale；否则仅在 type=scale 时使用通用 from/to
        float fromScale = hasFloat(obj, "fromScale")
                ? getFloat(obj, "fromScale", 0f)
                : (type.equals("scale") && hasFloat(obj, "from")) ? getFloat(obj, "from", 0f) : 0f;
        float toScale = hasFloat(obj, "toScale")
                ? getFloat(obj, "toScale", 0f)
                : (type.equals("scale") && hasFloat(obj, "to")) ? getFloat(obj, "to", 0f) : 0f;
        data.setFromScale(fromScale);
        data.setToScale(toScale);

        // alpha 字段：优先 fromAlpha/toAlpha，其次通用 from/to；默认 NaN 表示未指定
        float fromAlpha = hasFloat(obj, "fromAlpha") ? getFloat(obj, "fromAlpha", Float.NaN)
                        : (type.equals("fade") && hasFloat(obj, "from")) ? getFloat(obj, "from", Float.NaN) : Float.NaN;
        float toAlpha = hasFloat(obj, "toAlpha") ? getFloat(obj, "toAlpha", Float.NaN)
                      : (type.equals("fade") && hasFloat(obj, "to")) ? getFloat(obj, "to", Float.NaN) : Float.NaN;
        data.setFromAlpha(fromAlpha);
        data.setToAlpha(toAlpha);

        // rotation 字段：优先 fromRotation/toRotation，其次通用 from/to
        float fromRotation = hasFloat(obj, "fromRotation") ? getFloat(obj, "fromRotation", 0f)
                           : (type.equals("rotate") && hasFloat(obj, "from")) ? getFloat(obj, "from", 0f) : 0f;
        float toRotation = hasFloat(obj, "toRotation") ? getFloat(obj, "toRotation", 0f)
                         : (type.equals("rotate") && hasFloat(obj, "to")) ? getFloat(obj, "to", 0f) : 0f;
        data.setFromRotation(fromRotation);
        data.setToRotation(toRotation);

        // shake 参数
        data.setIntensity(getFloat(obj, "intensity", 4f));
        data.setFrequency(getFloat(obj, "frequency", 0.08f));

        // 通用参数
        data.setDurationMs(getInt(obj, "duration", 500));
        data.setDelayMs(getInt(obj, "delay", 0));
        if (obj.has("easing") && !obj.get("easing").isJsonNull()) {
            data.setEasing(obj.get("easing").getAsString());
        }

        return data;
    }

    @Override
    public JsonElement serialize(PortraitAnimationData src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", src.getType());
        putNum(obj, "fromX", src.getFromX());
        putNum(obj, "toX", src.getToX());
        putNum(obj, "fromY", src.getFromY());
        putNum(obj, "toY", src.getToY());
        putNum(obj, "fromScale", src.getFromScale());
        putNum(obj, "toScale", src.getToScale());
        // fromAlpha / toAlpha 可能是 NaN（未指定），必须跳过，否则 Gson 序列化会抛异常
        putNum(obj, "fromAlpha", src.getFromAlpha());
        putNum(obj, "toAlpha", src.getToAlpha());
        putNum(obj, "fromRotation", src.getFromRotation());
        putNum(obj, "toRotation", src.getToRotation());
        putNum(obj, "intensity", src.getIntensity());
        putNum(obj, "frequency", src.getFrequency());
        obj.addProperty("duration", src.getDurationMs());
        obj.addProperty("delay", src.getDelayMs());
        obj.addProperty("easing", src.getEasing());
        return obj;
    }

    private static void putNum(JsonObject obj, String key, float value) {
        // 仅当有限时写出，规避 Gson 对 NaN/Infinity 的序列化异常
        if (Float.isFinite(value)) {
            obj.addProperty(key, value);
        }
    }

    private static boolean hasFloat(JsonObject obj, String key) {
        return obj.has(key) && !obj.get(key).isJsonNull();
    }

    private static float getFloat(JsonObject obj, String key, float def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsFloat();
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static int getInt(JsonObject obj, String key, int def) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) return def;
        try {
            return obj.get(key).getAsInt();
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
