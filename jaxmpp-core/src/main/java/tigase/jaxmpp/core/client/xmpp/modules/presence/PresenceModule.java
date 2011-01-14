package tigase.jaxmpp.core.client.xmpp.modules.presence;

import tigase.jaxmpp.core.client.JID;
import tigase.jaxmpp.core.client.PacketWriter;
import tigase.jaxmpp.core.client.SessionObject;
import tigase.jaxmpp.core.client.XMPPException;
import tigase.jaxmpp.core.client.criteria.Criteria;
import tigase.jaxmpp.core.client.criteria.ElementCriteria;
import tigase.jaxmpp.core.client.exceptions.JaxmppException;
import tigase.jaxmpp.core.client.observer.BaseEvent;
import tigase.jaxmpp.core.client.observer.EventType;
import tigase.jaxmpp.core.client.observer.Listener;
import tigase.jaxmpp.core.client.observer.Observable;
import tigase.jaxmpp.core.client.xml.XMLException;
import tigase.jaxmpp.core.client.xmpp.modules.AbstractStanzaModule;
import tigase.jaxmpp.core.client.xmpp.modules.presence.PresenceStore.Handler;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence;
import tigase.jaxmpp.core.client.xmpp.stanzas.Presence.Show;
import tigase.jaxmpp.core.client.xmpp.stanzas.StanzaType;

public class PresenceModule extends AbstractStanzaModule<Presence> {

	public static class PresenceEvent extends BaseEvent {

		private static final long serialVersionUID = 1L;

		private boolean cancelled;

		private JID jid;

		private Presence presence;

		private Integer priority;

		private Show show;

		private String status;

		public PresenceEvent(EventType type) {
			super(type);
		}

		public void cancel() {
			this.cancelled = true;
		}

		public JID getJid() {
			return jid;
		}

		public Presence getPresence() {
			return presence;
		}

		public Integer getPriority() {
			return priority;
		}

		public Show getShow() {
			return show;
		}

		public String getStatus() {
			return status;
		}

		public boolean isCancelled() {
			return cancelled;
		}

		public void setJid(JID jid) {
			this.jid = jid;
		}

		public void setPresence(Presence presence) {
			this.presence = presence;
		}

		public void setPriority(Integer priority) {
			this.priority = priority;
		}

		public void setShow(Show show) {
			this.show = show;
		}

		public void setStatus(String status) {
			this.status = status;
		}
	}

	public static final EventType BeforeInitialPresence = new EventType();

	public static final EventType ContactAvailable = new EventType();

	public static final EventType ContactChangedPresence = new EventType();

	public static final EventType ContactUnavailable = new EventType();

	public static final Criteria CRIT = ElementCriteria.name("presence");

	public static final EventType SubscribeRequest = new EventType();

	private final Observable observable = new Observable();

	public PresenceModule(SessionObject sessionObject, PacketWriter packetWriter) {
		super(sessionObject, packetWriter);
		this.sessionObject.getPresence().setHandler(new Handler() {

			@Override
			public void setPresence(Show show, String status, Integer priority) throws XMLException, JaxmppException {
				PresenceModule.this.setPresence(show, status, priority);
			}
		});
	}

	public void addListener(EventType eventType, Listener<PresenceEvent> listener) {
		observable.addListener(eventType, listener);
	}

	public void addListener(Listener<PresenceEvent> listener) {
		observable.addListener(listener);
	}

	@Override
	public Criteria getCriteria() {
		return CRIT;
	}

	@Override
	public String[] getFeatures() {
		return null;
	}

	public PresenceStore getPresence() {
		return this.sessionObject.getPresence();
	}

	@Override
	public void process(final Presence presence) throws XMPPException, XMLException {
		final JID fromJid = presence.getFrom();
		log.finest("Presence received from " + fromJid + " :: " + presence.getAsString());
		if (fromJid == null)
			return;

		boolean availableOld = sessionObject.getPresence().isAvailable(fromJid.getBareJid());
		sessionObject.getPresence().update(presence);
		boolean availableNow = sessionObject.getPresence().isAvailable(fromJid.getBareJid());

		final StanzaType type = presence.getType();

		PresenceEvent event;
		if (type == StanzaType.subscribe) {
			// subscribe
			log.finer("Subscribe from " + fromJid);
			event = new PresenceEvent(SubscribeRequest);
		} else if (!availableOld && availableNow) {
			// sontact available
			log.finer("Presence online from " + fromJid);
			event = new PresenceEvent(ContactAvailable);
		} else if (availableOld && !availableNow) {
			// contact unavailable
			log.finer("Presence offline from " + fromJid);
			event = new PresenceEvent(ContactUnavailable);
		} else {
			log.finer("Presence change from " + fromJid);
			event = new PresenceEvent(ContactChangedPresence);
		}
		event.setPresence(presence);
		event.setJid(fromJid);
		event.setShow(presence.getShow());
		event.setStatus(presence.getStatus());
		event.setPriority(presence.getPriority());

		observable.fireEvent(event);
	}

	public void removeListener(EventType eventType, Listener<? extends BaseEvent> listener) {
		observable.removeListener(eventType, listener);
	}

	public void sendInitialPresence() throws XMLException, JaxmppException {
		PresenceEvent event = new PresenceEvent(BeforeInitialPresence);
		observable.fireEvent(event);

		if (event.isCancelled())
			return;

		Presence presence = Presence.create();
		presence.setPriority(event.getPriority());
		presence.setStatus(event.getStatus());
		presence.setShow(event.getShow());
		if (sessionObject.getProperty(SessionObject.NICKNAME) != null) {
			presence.setNickname((String) sessionObject.getProperty(SessionObject.NICKNAME));
		}

		writer.write(presence);
	}

	public void setPresence(Show show, String status, Integer priority) throws XMLException, JaxmppException {
		Presence presence = Presence.create();
		presence.setShow(show);
		presence.setStatus(status);
		presence.setPriority(priority);

		writer.write(presence);
	}

}