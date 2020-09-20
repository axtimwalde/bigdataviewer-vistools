package sc.fiji.bdvpg.services.serializers;

import bdv.SpimSource;
import bdv.img.WarpedSource;
import bdv.tools.transformation.TransformedSource;
import bdv.util.Procedural3DImageShort;
import bdv.util.ResampledSource;
import bdv.viewer.SourceAndConverter;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.imglib2.display.ColorConverter;
import net.imglib2.type.numeric.ARGBType;
import sc.fiji.bdvpg.services.SourceAndConverterSerializer;
import sc.fiji.bdvpg.services.SourceAndConverterServices;
import sc.fiji.bdvpg.sourceandconverter.display.ColorChanger;

import java.lang.reflect.Type;

public class SourceAndConverterAdapter implements JsonSerializer<SourceAndConverter>,
        JsonDeserializer<SourceAndConverter> {

    SourceAndConverterSerializer sacSerializer;

    public SourceAndConverterAdapter(SourceAndConverterSerializer sacSerializer) {
        this.sacSerializer = sacSerializer;
    }

    @Override
    public JsonElement serialize(SourceAndConverter sourceAndConverter,
                                 Type type,
                                 JsonSerializationContext jsonSerializationContext) {
        try {
            JsonObject obj = new JsonObject();
            obj.addProperty("source_name", sourceAndConverter.getSpimSource().getName());
            obj.addProperty("source_class", sourceAndConverter.getSpimSource().getClass().getName());
            obj.addProperty("converter_class", sourceAndConverter.getConverter().getClass().toString());
            obj.addProperty("source_id", sacSerializer.getSacToId().get(sourceAndConverter));

            if (sourceAndConverter.getConverter() instanceof ColorConverter) {
                ColorConverter colorConverter = (ColorConverter) sourceAndConverter.getConverter();
                obj.add("color", jsonSerializationContext.serialize(colorConverter.getColor().get()));
                double min = SourceAndConverterServices.getSourceAndConverterDisplayService()
                        .getConverterSetup(sourceAndConverter).getDisplayRangeMin();
                double max = SourceAndConverterServices.getSourceAndConverterDisplayService()
                        .getConverterSetup(sourceAndConverter).getDisplayRangeMax();
                obj.addProperty("converter_setup_min", min);
                obj.addProperty("converter_setup_max", max);
            }

            JsonElement element = serializeSubClass(sourceAndConverter, SourceAndConverter.class, jsonSerializationContext);
            obj.add("sac", element);

            return obj;
        } catch (UnsupportedOperationException e) {
            System.err.println("Could not serialize source "+ sourceAndConverter.getSpimSource().getName());
            return null;
        }
    }

    JsonElement serializeSubClass (SourceAndConverter sourceAndConverter,
                                          Type type,
                                          JsonSerializationContext jsonSerializationContext) throws UnsupportedOperationException {

        if (sourceAndConverter.getSpimSource() instanceof SpimSource) {
            return SpimSourceAndConverterAdapter.serialize(sourceAndConverter, SourceAndConverter.class, jsonSerializationContext);
        }
        if (sourceAndConverter.getSpimSource() instanceof TransformedSource) {
            return new TransformedSourceAndConverterAdapter(sacSerializer).serialize(sourceAndConverter, SourceAndConverter.class, jsonSerializationContext);
        }
        if (sourceAndConverter.getSpimSource() instanceof ResampledSource) {
            throw new UnsupportedOperationException();
        }
        if (sourceAndConverter.getSpimSource() instanceof WarpedSource) {
            return new WarpedSourceAndConverterAdapter(sacSerializer).serialize(sourceAndConverter, SourceAndConverter.class, jsonSerializationContext);
        }

        System.out.println("Unsupported serialisation of "+sourceAndConverter.getSpimSource().getClass().getName());

        throw new UnsupportedOperationException();
    }

    @Override
    public SourceAndConverter deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
        JsonObject jsonObject = jsonElement.getAsJsonObject();

        String sourceClass = jsonObject.getAsJsonPrimitive("source_class").getAsString();

        SourceAndConverter sac = null;

        if (sourceClass.equals(SpimSource.class.getName())) {
            sac = SpimSourceAndConverterAdapter.deserialize(jsonObject.get("sac"), SourceAndConverter.class, jsonDeserializationContext);
        } else if (sourceClass.equals(TransformedSource.class.getName())) {
            sac = new TransformedSourceAndConverterAdapter(sacSerializer).deserialize(jsonObject.get("sac"), SourceAndConverter.class, jsonDeserializationContext);
        } else if (sourceClass.equals(WarpedSource.class.getName())) {
            sac = new WarpedSourceAndConverterAdapter(sacSerializer).deserialize(jsonObject.get("sac"), SourceAndConverter.class, jsonDeserializationContext);
        } else {
            System.err.println("Could not deserialise source of class "+sourceClass);
        }

        if (sac != null) {
            if (jsonObject.getAsJsonPrimitive("color")!=null) {
                // Now the color
                int color = jsonObject.getAsJsonPrimitive("color").getAsInt();
                new ColorChanger(sac,  new ARGBType(color)).run(); // TO deal with volatile and non volatile
                // Min Max display
                SourceAndConverterServices.getSourceAndConverterDisplayService()
                        .getConverterSetup(sac).setDisplayRange(
                                jsonObject.getAsJsonPrimitive("converter_setup_min").getAsDouble(),
                                jsonObject.getAsJsonPrimitive("converter_setup_max").getAsDouble());
            }

            // unique identifier
            int idSource = jsonObject.getAsJsonPrimitive("source_id").getAsInt();
            sacSerializer.getIdToSac().put(idSource, sac);
            sacSerializer.getSacToId().put(sac, idSource);
            sacSerializer.getSourceToId().put(sac.getSpimSource(), idSource);
            sacSerializer.getIdToSource().put(idSource, sac.getSpimSource());
            sacSerializer.alreadyDeSerializedSacs.add(idSource);
            return sac;
        }

        return null;
    }
}