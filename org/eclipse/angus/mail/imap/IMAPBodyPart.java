package org.eclipse.angus.mail.imap;

import jakarta.activation.DataHandler;
import jakarta.mail.FolderClosedException;
import jakarta.mail.Header;
import jakarta.mail.IllegalWriteException;
import jakarta.mail.MessagingException;
import jakarta.mail.Multipart;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeUtility;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.io.UnsupportedEncodingException;
import java.util.Enumeration;
import org.eclipse.angus.mail.iap.ConnectionException;
import org.eclipse.angus.mail.iap.ProtocolException;
import org.eclipse.angus.mail.imap.protocol.BODY;
import org.eclipse.angus.mail.imap.protocol.BODYSTRUCTURE;
import org.eclipse.angus.mail.imap.protocol.IMAPProtocol;
import org.eclipse.angus.mail.util.LineOutputStream;
import org.eclipse.angus.mail.util.PropUtil;
import org.eclipse.angus.mail.util.ReadableMime;
import org.eclipse.angus.mail.util.SharedByteArrayOutputStream;

public class IMAPBodyPart extends MimeBodyPart implements ReadableMime {
	private IMAPMessage message;
	private BODYSTRUCTURE bs;
	private String sectionId;
	private String type;
	private String description;
	private boolean headersLoaded = false;
	private static final boolean decodeFileName = PropUtil.getBooleanSystemProperty("mail.mime.decodefilename", false);

	protected IMAPBodyPart(BODYSTRUCTURE bs, String sid, IMAPMessage message) {
		this.bs = bs;
		this.sectionId = sid;
		this.message = message;
		ContentType ct = new ContentType(bs.type, bs.subtype, bs.cParams);
		this.type = ct.toString();
	}

	protected void updateHeaders() {
	}

	public int getSize() throws MessagingException {
		return this.bs.size;
	}

	public int getLineCount() throws MessagingException {
		return this.bs.lines;
	}

	public String getContentType() throws MessagingException {
		return this.type;
	}

	public String getDisposition() throws MessagingException {
		return this.bs.disposition;
	}

	public void setDisposition(String disposition) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public String getEncoding() throws MessagingException {
		return this.bs.encoding;
	}

	public String getContentID() throws MessagingException {
		return this.bs.id;
	}

	public String getContentMD5() throws MessagingException {
		return this.bs.md5;
	}

	public void setContentMD5(String md5) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public String getDescription() throws MessagingException {
		if (this.description != null) {
			return this.description;
		} else if (this.bs.description == null) {
			return null;
		} else {
			try {
				this.description = MimeUtility.decodeText(this.bs.description);
			} catch (UnsupportedEncodingException var2) {
				this.description = this.bs.description;
			}

			return this.description;
		}
	}

	public void setDescription(String description, String charset) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public String getFileName() throws MessagingException {
		String filename = null;
		if (this.bs.dParams != null) {
			filename = this.bs.dParams.get("filename");
		}

		if ((filename == null || filename.isEmpty()) && this.bs.cParams != null) {
			filename = this.bs.cParams.get("name");
		}

		if (decodeFileName && filename != null) {
			try {
				filename = MimeUtility.decodeText(filename);
			} catch (UnsupportedEncodingException var3) {
				throw new MessagingException("Can't decode filename", var3);
			}
		}

		return filename;
	}

	public void setFileName(String filename) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	protected InputStream getContentStream() throws MessagingException {
		InputStream is = null;
		boolean pk = this.message.getPeek();
		synchronized (this.message.getMessageCacheLock()) {
			label51 : {
				IMAPInputStream var10000;
				try {
					IMAPProtocol p = this.message.getProtocol();
					this.message.checkExpunged();
					if (!p.isREV1() || this.message.getFetchBlockSize() == -1) {
						int seqnum = this.message.getSequenceNumber();
						BODY b;
						if (pk) {
							b = p.peekBody(seqnum, this.sectionId);
						} else {
							b = p.fetchBody(seqnum, this.sectionId);
						}

						if (b != null) {
							is = b.getByteArrayInputStream();
						}
						break label51;
					}

					var10000 = new IMAPInputStream(this.message, this.sectionId,
							this.message.ignoreBodyStructureSize() ? -1 : this.bs.size, pk);
				} catch (ConnectionException var8) {
					throw new FolderClosedException(this.message.getFolder(), var8.getMessage());
				} catch (ProtocolException var9) {
					throw new MessagingException(var9.getMessage(), var9);
				}

				return var10000;
			}
		}

		if (is == null) {
			this.message.forceCheckExpunged();
			is = new ByteArrayInputStream(new byte[0]);
		}

		return is;
	}

	private InputStream getHeaderStream() throws MessagingException {
		if (!this.message.isREV1()) {
			this.loadHeaders();
		}

		synchronized (this.message.getMessageCacheLock()) {
			InputStream var10000;
			try {
				IMAPProtocol p = this.message.getProtocol();
				this.message.checkExpunged();
				if (p.isREV1()) {
					int seqnum = this.message.getSequenceNumber();
					BODY b = p.peekBody(seqnum, this.sectionId + ".MIME");
					if (b == null) {
						throw new MessagingException("Failed to fetch headers");
					}

					ByteArrayInputStream bis = b.getByteArrayInputStream();
					if (bis == null) {
						throw new MessagingException("Failed to fetch headers");
					}

					ByteArrayInputStream var26 = bis;
					return var26;
				}

				SharedByteArrayOutputStream bos = new SharedByteArrayOutputStream(0);
				LineOutputStream los = new LineOutputStream(bos);

				try {
					Enumeration<String> hdrLines = super.getAllHeaderLines();

					while (true) {
						if (!hdrLines.hasMoreElements()) {
							los.writeln();
							break;
						}

						los.writeln((String) hdrLines.nextElement());
					}
				} catch (IOException var18) {
				} finally {
					try {
						los.close();
					} catch (IOException var17) {
					}

				}

				var10000 = bos.toStream();
			} catch (ConnectionException var20) {
				throw new FolderClosedException(this.message.getFolder(), var20.getMessage());
			} catch (ProtocolException var21) {
				throw new MessagingException(var21.getMessage(), var21);
			}

			return var10000;
		}
	}

	public InputStream getMimeStream() throws MessagingException {
		return new SequenceInputStream(this.getHeaderStream(), this.getContentStream());
	}

	public synchronized DataHandler getDataHandler() throws MessagingException {
		if (this.dh == null) {
			if (this.bs.isMulti()) {
				this.dh = new DataHandler(
						new IMAPMultipartDataSource(this, this.bs.bodies, this.sectionId, this.message));
			} else if (this.bs.isNested() && this.message.isREV1() && this.bs.envelope != null) {
				this.dh = new DataHandler(
						new IMAPNestedMessage(this.message, this.bs.bodies[0], this.bs.envelope, this.sectionId),
						this.type);
			}
		}

		return super.getDataHandler();
	}

	public void setDataHandler(DataHandler content) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public void setContent(Object o, String type) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public void setContent(Multipart mp) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public String[] getHeader(String name) throws MessagingException {
		this.loadHeaders();
		return super.getHeader(name);
	}

	public void setHeader(String name, String value) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public void addHeader(String name, String value) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public void removeHeader(String name) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public Enumeration<Header> getAllHeaders() throws MessagingException {
		this.loadHeaders();
		return super.getAllHeaders();
	}

	public Enumeration<Header> getMatchingHeaders(String[] names) throws MessagingException {
		this.loadHeaders();
		return super.getMatchingHeaders(names);
	}

	public Enumeration<Header> getNonMatchingHeaders(String[] names) throws MessagingException {
		this.loadHeaders();
		return super.getNonMatchingHeaders(names);
	}

	public void addHeaderLine(String line) throws MessagingException {
		throw new IllegalWriteException("IMAPBodyPart is read-only");
	}

	public Enumeration<String> getAllHeaderLines() throws MessagingException {
		this.loadHeaders();
		return super.getAllHeaderLines();
	}

	public Enumeration<String> getMatchingHeaderLines(String[] names) throws MessagingException {
		this.loadHeaders();
		return super.getMatchingHeaderLines(names);
	}

	public Enumeration<String> getNonMatchingHeaderLines(String[] names) throws MessagingException {
		this.loadHeaders();
		return super.getNonMatchingHeaderLines(names);
	}

	private synchronized void loadHeaders() throws MessagingException {
		if (!this.headersLoaded) {
			if (this.headers == null) {
				this.headers = new InternetHeaders();
			}

			synchronized (this.message.getMessageCacheLock()) {
				try {
					IMAPProtocol p = this.message.getProtocol();
					this.message.checkExpunged();
					if (p.isREV1()) {
						int seqnum = this.message.getSequenceNumber();
						BODY b = p.peekBody(seqnum, this.sectionId + ".MIME");
						if (b == null) {
							throw new MessagingException("Failed to fetch headers");
						}

						ByteArrayInputStream bis = b.getByteArrayInputStream();
						if (bis == null) {
							throw new MessagingException("Failed to fetch headers");
						}

						this.headers.load(bis);
					} else {
						this.headers.addHeader("Content-Type", this.type);
						this.headers.addHeader("Content-Transfer-Encoding", this.bs.encoding);
						if (this.bs.description != null) {
							this.headers.addHeader("Content-Description", this.bs.description);
						}

						if (this.bs.id != null) {
							this.headers.addHeader("Content-ID", this.bs.id);
						}

						if (this.bs.md5 != null) {
							this.headers.addHeader("Content-MD5", this.bs.md5);
						}
					}
				} catch (ConnectionException var7) {
					throw new FolderClosedException(this.message.getFolder(), var7.getMessage());
				} catch (ProtocolException var8) {
					throw new MessagingException(var8.getMessage(), var8);
				}
			}

			this.headersLoaded = true;
		}
	}
}