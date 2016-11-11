package repacker.model.anim;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.stream.Collectors;

import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Quaternion;
import com.badlogic.gdx.math.Vector3;

import repacker.model.TMD_File;
import repacker.model.TMD_IO;

public class TMD_Animation extends TMD_IO {
	public final String name;
	public final float length;
	public final TMD_Channel[] channels;

	public TMD_Animation(TMD_File file, ByteBuffer data) throws UnsupportedEncodingException {
		super(file);
		byte namelen = data.get();
		name = read(data, 23);
		int channelCount = data.getInt(); // not accurate
		System.out.println(name + "\t" + channelCount);

		length = data.getFloat();
		int[] nodeMeta = new int[file.scene.nodes.length];
		ints(data, nodeMeta);
		channels = new TMD_Channel[file.scene.nodes.length];
		for (int c = 0; c < channels.length; c++) {
			channels[c] = new TMD_Channel(file, data);
			channels[c].anim_NodeMeta = nodeMeta[c];
		}
		System.out.println();
	}

	@Override
	public void link() {
		for (TMD_Channel c : channels)
			c.link();

		// Link nodeRef:
		for (int i = 1; i < channels.length; i++)
			channels[i].nodeRef = file.scene.nodes[i-1];

		// Localize
		// Make a set of all keyframe times
		TreeSet<Float> flts = Arrays.stream(channels).filter(a -> a.nodeRef != null)
				.flatMap(a -> Arrays.stream(a.frames)).map(a -> a.time)
				.collect(Collectors.toCollection(() -> new TreeSet<Float>()));

		Quaternion tmpQ = new Quaternion();
		Vector3 tmp3 = new Vector3();

		Matrix4[] pose = new Matrix4[channels.length];
		for (int i = 0; i < pose.length; i++)
			pose[i] = new Matrix4();

		int[] frames = new int[channels.length];

		for (float t : flts) {
			// Build a current pose set.
			for (int i = 0; i < channels.length; i++) {
				if (channels[i].nodeRef == null)
					continue;
				frames[i] = channels[i].value(t, tmp3, tmpQ, false);
				pose[i].set(tmp3, tmpQ);//.mulLeft(channels[i].nodeRef.localPosition);
			}
			// Find matching frames.
			for (int i = 0; i < channels.length; i++) {
				if (channels[i].nodeRef == null)
					continue;
				int frame = frames[i];
				TMD_KeyFrame b = channels[i].frames[frame];
				if (frame < channels[i].frames.length - 1 && b.time != t)
					b = channels[i].frames[frame + 1];
				if (b.time == t) {
					pose[i].getTranslation(b.localPos);
					pose[i].getRotation(b.localRot);
					channels[i].nodeRef.localPosition.getTranslation(b.localPos);
				}
			}
		}
	}
}
