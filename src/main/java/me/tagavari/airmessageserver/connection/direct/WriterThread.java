package me.tagavari.airmessageserver.connection.direct;

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class WriterThread extends Thread {
	//Creating the queue
	private final BlockingQueue<PacketStruct> uploadQueue = new LinkedBlockingQueue<>();
	
	private final Collection<ClientSocket> clientList;
	
	public WriterThread(Collection<ClientSocket> clientList) {
		this.clientList = clientList;
	}
	
	@Override
	public void run() {
		try {
			while(!isInterrupted()) {
				PacketStruct packet = uploadQueue.take();
				if(packet.target == null) {
					for(ClientSocket client : clientList) {
						if(!client.isClientRegistered()) continue;
						client.sendDataSync(packet.type, packet.content);
					}
				} else {
					packet.target.sendDataSync(packet.type, packet.content);
				}
				if(packet.sentRunnable != null) packet.sentRunnable.run();
			}
		} catch(InterruptedException exception) {
			return;
		}
	}
	
	void sendPacket(PacketStruct packet) {
		uploadQueue.add(packet);
	}
	
	static class PacketStruct {
		final ClientSocket target;
		final int type;
		final byte[] content;
		Runnable sentRunnable = null;
		
		PacketStruct(ClientSocket target, int type, byte[] content) {
			this.target = target;
			this.type = type;
			this.content = content;
		}
		
		PacketStruct(ClientSocket target, int type, byte[] content, Runnable sentRunnable) {
			this(target, type, content);
			this.sentRunnable = sentRunnable;
		}
	}
}