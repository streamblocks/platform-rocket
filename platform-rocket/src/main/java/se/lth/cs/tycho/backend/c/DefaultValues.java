package se.lth.cs.tycho.backend.c;

import org.multij.Module;
import se.lth.cs.tycho.type.AlgebraicType;
import se.lth.cs.tycho.type.BoolType;
import se.lth.cs.tycho.type.CallableType;
import se.lth.cs.tycho.type.IntType;
import se.lth.cs.tycho.type.ListType;
import se.lth.cs.tycho.type.RealType;
import se.lth.cs.tycho.type.Type;

@Module
public interface DefaultValues {
	String defaultValue(Type type);
	default String defaultValue(CallableType t) {
		return "{ NULL, NULL }";
	}
	default String defaultValue(BoolType t) {
		return "false";
	}
	default String defaultValue(RealType t) {
		return "0.0";
	}
	default String defaultValue(IntType t) {
		return "0";
	}
	default String defaultValue(ListType t) {
		if (t.getSize().isPresent()) {
			StringBuilder builder = new StringBuilder();
			String element = defaultValue(t.getElementType());
			builder.append("{");
			for (int i = 0; i < t.getSize().getAsInt(); i++) {
				if (i > 0) {
					builder.append(", ");
				}
				builder.append(element);
			}
			builder.append("}");
			return builder.toString();
		} else {
			throw new UnsupportedOperationException("Not implemented");
		}
	}
	default String defaultValue(AlgebraicType t) {
		return "NULL";
	}
}
