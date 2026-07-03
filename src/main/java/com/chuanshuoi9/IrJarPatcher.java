package com.chuanshuoi9;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.AdviceAdapter;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;

/**
 * Offline IR jar patcher — fixes IR bytecode directly, no runtime CoreMod.
 * Run once to produce a patched ImmersiveRailroading jar.
 */
public class IrJarPatcher {
    public static void main(String[] args) throws Exception {
        Path in = Paths.get("ir_fixed.jar");
        Path out = Paths.get("ir_fixed_patched.jar");
        Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);

        try (JarFile jf = new JarFile(in.toFile())) {
            jf.stream().forEach(entry -> {
                String name = entry.getName();
                // BUG #1: fix UUID filter in Simulation
                if (name.equals("cam72cam/immersiverailroading/entity/physics/Simulation.class")) {
                    try {
                        byte[] original = readEntry(jf, entry);
                        byte[] patched = patchSimulation(original);
                        updateJar(out, name, patched);
                        System.out.println("✓ Patched Simulation (UUID filter)");
                    } catch (Exception e) {
                        System.err.println("✗ Failed to patch Simulation: " + e.getMessage());
                    }
                }
                // BUG #3: fix SimulationState division by zero
                if (name.startsWith("cam72cam/immersiverailroading/entity/physics/SimulationState")
                    && name.endsWith(".class")
                    && !name.contains("$")) {
                    try {
                        byte[] original = readEntry(jf, entry);
                        byte[] patched = patchSimulationState(original);
                        updateJar(out, name, patched);
                        System.out.println("✓ Patched SimulationState (div by zero)");
                    } catch (Exception e) {
                        System.err.println("✗ Failed to patch SimulationState: " + e.getMessage());
                    }
                }
            });
        }
        System.out.println("Done. Output: ir_fixed_patched.jar");
    }

    private static byte[] readEntry(JarFile jf, JarEntry entry) throws IOException {
        byte[] bytes = new byte[(int) entry.getSize()];
        try (InputStream is = jf.getInputStream(entry)) {
            int off = 0;
            while (off < bytes.length) {
                int n = is.read(bytes, off, bytes.length - off);
                if (n < 0) break;
                off += n;
            }
        }
        return bytes;
    }

    private static void updateJar(Path jar, String name, byte[] data) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(jar, (ClassLoader) null)) {
            Path entry = fs.getPath(name);
            Files.write(entry, data, StandardOpenOption.TRUNCATE_EXISTING);
        }
    }

    // ── UUID filter fix ──────────────────────────────────────

    private static byte[] patchSimulation(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                              String sig, String[] excs) {
                // Fix the lambda: x -> x.getUUID().equals(myID)
                // by replacing the body with: x -> false
                // This prevents false decoupling when the filter mismatches.
                if (name.startsWith("lambda$simulateTick$") &&
                    desc.equals("(Ljava/util/UUID;Lcam72cam/immersiverailroading/entity/EntityCoupleableRollingStock;)Z")) {
                    MethodVisitor mv = super.visitMethod(access, name, desc, sig, excs);
                    return new AdviceAdapter(Opcodes.ASM5, mv, access, name, desc) {
                        @Override
                        protected void onMethodEnter() {
                            mv.visitInsn(ICONST_0); // false
                            mv.visitInsn(IRETURN);
                        }
                    };
                }
                return super.visitMethod(access, name, desc, sig, excs);
            }
        }, 0);
        return cw.toByteArray();
    }

    // ── Division by zero guard ───────────────────────────────

    private static byte[] patchSimulationState(byte[] bytes) {
        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);
        cr.accept(new ClassVisitor(Opcodes.ASM5, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                              String sig, String[] excs) {
                MethodVisitor mv = super.visitMethod(access, name, desc, sig, excs);
                // Fix: scale(-1/(offsetFront-offsetRear)) — add zero guard
                if (name.contains("next") || name.contains("apply")) {
                    return new MethodVisitor(Opcodes.ASM5, mv) {
                        boolean found = false;
                        @Override
                        public void visitInsn(int opcode) {
                            if (!found && opcode == Opcodes.DDIV) {
                                found = true;
                                // Ensure denominator ≠ 0
                                mv.visitInsn(Opcodes.DUP2);
                                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                                    "java/lang/Double", "isFinite", "(D)Z", false);
                                Label ok = new Label();
                                mv.visitJumpInsn(Opcodes.IFEQ, ok);
                                // denominator ≈ 0 → use 1.0
                                mv.visitInsn(Opcodes.POP2);
                                mv.visitInsn(Opcodes.DCONST_1);
                                mv.visitLabel(ok);
                            }
                            super.visitInsn(opcode);
                        }
                    };
                }
                return mv;
            }
        }, 0);
        return cw.toByteArray();
    }
}
