# Privacy

Paper is an Avyntora product. This policy describes the Paper Android app.

Paper has no advertising, accounts, subscriptions, analytics SDKs, automatic crash uploads, or cloud document conversion.

Documents are imported and prepared in app-private storage. USB output goes to the selected, authorised printer. Network output goes directly to the printer selected in Android's Print menu. Printing necessarily shares the document with that printer and its operator; a print server may keep its own history.

The INTERNET permission is used for printer discovery/capability queries, submission and status. Paper does not contact an advertising or analytics service. Network discovery looks for IPP/IPPS services on the local network. Manually entered addresses are used only as printer endpoints, with HTTP redirects disabled.

Plain IPP does not encrypt the connection. Paper labels it accordingly. IPPS uses the device's normal certificate verification; unsupported/self-signed certificates are not bypassed automatically. This version does not collect printer passwords.

Settings, custom presets, saved network printer addresses and capabilities, and manually confirmed duplex instructions are stored locally. App backup is disabled. Saved printer addresses can be removed through the printer screen. No document contents are included in saved presets.

Prepared system-print documents are deleted from Paper's temporary storage when its service finishes handling the job. Android's print spooler and the printer have their own storage and retention rules. Imported and prepared files used in the main app remain in its private cache until cleared or reclaimed; do not describe this version as securely erasing every document immediately after printing.

USB diagnostic reports are copied only when the user chooses. They contain app/platform and printer/transfer information. Review reports before publishing them. Never attach confidential documents, passwords or printer serial numbers to a compatibility report.
