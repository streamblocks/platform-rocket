package se.lth.cs.tycho.backend.c;

import org.multij.Binding;
import org.multij.BindingKind;
import org.multij.Module;
import se.lth.cs.tycho.ir.entity.am.ActorMachine;
import se.lth.cs.tycho.ir.entity.am.ctrl.Exec;
import se.lth.cs.tycho.ir.entity.am.ctrl.Instruction;
import se.lth.cs.tycho.ir.entity.am.ctrl.InstructionKind;
import se.lth.cs.tycho.ir.entity.am.ctrl.State;
import se.lth.cs.tycho.ir.entity.am.ctrl.Test;
import se.lth.cs.tycho.ir.entity.am.ctrl.Wait;
import se.lth.cs.tycho.attribute.ScopeLiveness;
import se.lth.cs.tycho.settings.Configuration;
import se.lth.cs.tycho.settings.OnOffSetting;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

@Module
public interface Controllers {
	@Binding(BindingKind.INJECTED)
	Backend backend();

	default Emitter emitter() {
		return backend().emitter();
	}

	default void emitControllerHeader(String name, ActorMachine actorMachine) {
		emitter().emit("_Bool %s_run(%1$s_state *self);", name);
	}

	OnOffSetting scopeLivenessAnalysis = new OnOffSetting() {
		@Override
		public String getKey() {
			return "scope-liveness-analysis";
		}

		@Override
		public String getDescription() {
			return "Analyzes actor machine scope liveness for initialization.";
		}

		@Override
		public Boolean defaultValue(Configuration configuration) {
			return true;
		}
	};


	default void emitController(String name, ActorMachine actorMachine) {
		List<? extends State> stateList = actorMachine.controller().getStateList();
		Map<State, Integer> stateMap = stateMap(stateList);
		Set<State> waitTargets = collectWaitTargets(stateList);

		emitter().emit("_Bool %s_run(%1$s_state *self) {", name);
		emitter().increaseIndentation();

		emitter().emit("_Bool progress = false;");
		emitter().emit("");

		jumpInto(waitTargets.stream().mapToInt(stateMap::get).collect(BitSet::new, BitSet::set, BitSet::or));

		Function<Instruction, BitSet> initialize;
		if (backend().context().getConfiguration().get(scopeLivenessAnalysis)) {
			ScopeLiveness liveness = new ScopeLiveness(backend().scopes(), actorMachine, backend().scopeDependencies());
			initialize = liveness::init;
		} else {
			initialize = instruction -> backend().scopes().init(actorMachine, instruction);
		}

		for (State s : stateList) {
			emitter().emit("S%d:", stateMap.get(s));
			Instruction instruction = s.getInstructions().get(0);
			initialize.apply(instruction).stream().forEach(scope ->
					emitter().emit("%s_init_scope_%d(self);", name, scope)
			);
			emitInstruction(name, instruction, stateMap);
		}

		emitter().decreaseIndentation();
		emitter().emit("}");
	}

	default Map<State, Integer> stateMap(List<? extends State> stateList) {
		int i = 0;
		Map<State, Integer> result = new HashMap<>();
		for (State s : stateList) {
			result.put(s, i++);
		}
		return result;
	}

	void emitInstruction(String name, Instruction instruction, Map<State, Integer> stateNumbers);

	default void emitInstruction(String name, Test test, Map<State, Integer> stateNumbers) {
		emitter().emit("if (%s_condition_%d(self)) {", name, test.condition());
		emitter().increaseIndentation();
		emitter().emit("goto S%d;", stateNumbers.get(test.targetTrue()));
		emitter().decreaseIndentation();
		emitter().emit("} else {");
		emitter().increaseIndentation();
		emitter().emit("goto S%d;", stateNumbers.get(test.targetFalse()));
		emitter().decreaseIndentation();
		emitter().emit("}");
		emitter().emit("");
	}

	default void emitInstruction(String name, Wait wait, Map<State, Integer> stateNumbers) {
		emitter().emit("self->program_counter = %d;", stateNumbers.get(wait.target()));
		emitter().emit("return progress;");
		emitter().emit("");
	}

	default void emitInstruction(String name, Exec exec, Map<State, Integer> stateNumbers) {
		emitter().emit("%s_transition_%d(self);", name, exec.transition());
		emitter().emit("progress = true;");
		emitter().emit("goto S%d;", stateNumbers.get(exec.target()));
		emitter().emit("");
	}

	default void jumpInto(BitSet waitTargets) {
		emitter().emit("switch (self->program_counter) {");
		waitTargets.stream().forEach(s -> emitter().emit("case %d: goto S%1$d;", s));
		emitter().emit("}");
		emitter().emit("");
	}

	default Set<State> collectWaitTargets(List<? extends State> stateList) {
		Set<State> targets = new HashSet<>();
		for (State state : stateList) {
			Instruction i = state.getInstructions().get(0);
			if (i.getKind() == InstructionKind.WAIT) {
				i.forEachTarget(targets::add);
			}
		}
		return targets;
	}

}
